"""Transformer of Fortran AST into Python AST."""

import logging
import typing as t
import xml.etree.ElementTree as ET

import numpy as np
import typed_ast.ast3 as typed_ast3
import typed_astunparse

_LOG = logging.getLogger(__name__)


class AstTransformer:

    """Transform Fortran AST in XML format into typed Python AST.

    The Fortran AST in XML format is provided by XML output generator for Open Fortran Parser.

    Typed Python AST is provided by typed-ast package.
    """

    def __init__(self):
        self.transforms = [f for f in dir(self) if not f.startswith('__')]

    def transform(self, node: ET.Element, warn: bool = True):
        if f'_{node.tag}' not in self.transforms:
            if warn:
                _LOG.warning('no transformer available for node "%s"', node.tag)
                _LOG.debug('%s', ET.tostring(node).decode().rstrip())
            return None
        _transform = getattr(self, f'_{node.tag}')
        return _transform(node)

    def transform_all_subnodes(self, node: ET.Element, warn: bool = True, skip_empty: bool = False):
        transformed = []
        for subnode in node:
            if skip_empty and not subnode.attrib and len(subnode) == 0:
                continue
            if f'_{subnode.tag}' not in self.transforms:
                if warn:
                    _LOG.warning('no transformer available for node "%s"', subnode.tag)
                    _LOG.debug('%s', ET.tostring(subnode).decode().rstrip())
                continue
            _transform = getattr(self, f'_{subnode.tag}')
            transformed.append(_transform(subnode))
        return transformed

    def _file(self, node: ET.Element) -> typed_ast3.AST:
        body = self.transform_all_subnodes(node, warn=False)
        return typed_ast3.Module(body=body, type_ignores=[])

    def _module(self, node):
        _LOG.warning('%s', ET.tostring(node).decode().rstrip())
        raise NotImplementedError()

    def _function(self, node: ET.Element):
        _LOG.warning('%s', ET.tostring(node).decode().rstrip())
        raise NotImplementedError()
        #return typed_ast3.FunctionDef()

    def _program(self, node: ET.Element) -> typed_ast3.AST:
        module = typed_ast3.parse('''if __name__ == '__main__':\n    pass''')
        body = self.transform_all_subnodes(node.find('./body'))
        for i in range(len(body) - 1, -1, -1):
            if isinstance(body[i], list):
                sublist = body[i]
                del body[i]
                for elem in reversed(sublist):
                    body.insert(i, elem)
        conditional = module.body[0]
        conditional.body = body
        return conditional

    def _specification(self, node: ET.Element) -> t.List[typed_ast3.AST]:
        declarations = self.transform_all_subnodes(node, warn=False, skip_empty=True)
        return declarations

    def _declaration(self, node: ET.Element) -> typed_ast3.AnnAssign:
        if node.attrib['type'] == 'implicit':
            # TODO: generate comment here maybe?
            if node.attrib['subtype'].lower() == 'none':
                annotation = typed_ast3.NameConstant(value=None)
            else:
                annotation = typed_ast3.Str(s=node.attrib['subtype'])
            return typed_ast3.AnnAssign(
                target=typed_ast3.Name(id='implicit'), annotation=annotation, value=None,
                simple=True)
        elif node.attrib['type'] == 'variable':
            return self._declaration_variable(node)
        elif node.attrib['type'] == 'include':
            return self._declaration_include(node)
        return typed_ast3.Expr(value=typed_ast3.Call(
            func=typed_ast3.Name(id='print'),
            args=[typed_ast3.Str(s='declaration'), typed_ast3.Str(s=node.attrib['type'])],
            keywords=[]))

    def _declaration_variable(
            self, node: ET.Element) -> t.Union[typed_ast3.Assign, typed_ast3.AnnAssign]:
        variables_node = node.find('./variables')
        if variables_node is None:
            _LOG.error('%s', ET.tostring(node).decode().rstrip())
            raise SyntaxError('"variables" node not present')
        variables = self.transform_all_subnodes(variables_node, warn=False, skip_empty=True)
        if not variables:
            _LOG.error('%s', ET.tostring(node).decode().rstrip())
            raise SyntaxError('at least one variable expected in variables list')
        if len(variables) == 1:
            target = variables[0][0]
        else:
            target = typed_ast3.Tuple(elts=[var[0] for var in variables])

        type_node = node.find('./type')
        if type_node is None:
            _LOG.error('%s', ET.tostring(node).decode().rstrip())
            raise SyntaxError('"type" node not present')
        annotation = self.transform(type_node)

        dimensions_node = node.find('./dimensions')
        if dimensions_node is not None:
            dimensions = self.transform(dimensions_node)
            # TODO: modify annotation accordingly
            raise NotImplementedError()

        value = typed_ast3.NameConstant(value=None)

        if len(variables) == 1:
            return typed_ast3.AnnAssign(
                target=target, annotation=annotation, value=value, simple=True)
        else:
            type_comment = typed_astunparse.unparse(
                    typed_ast3.Tuple(elts=[annotation for _ in range(len(variables))])).strip()
            return typed_ast3.Assign(targets=[target], value=value, type_comment=type_comment)

    def _declaration_include(self, node: ET.Element):
        file_node = node.find('./file')
        path_attrib = file_node.attrib['path']
        return typed_ast3.Import(names=[typed_ast3.alias(name=path_attrib,asname=None)])

    def _loop(self, node: ET.Element):
        if node.attrib['type'] == 'do':
            return self._loop_do(node)
        elif node.attrib['type'] == 'forall':
            return self._loop_forall(node)
        else:
            raise NotImplementedError()

    def _loop_do(self, node: ET.Element) -> typed_ast3.For:
        index_variable = node.find('./header/index-variable')
        target, iter_ = self._index_variable(index_variable)
        body = self.transform_all_subnodes(node.find('./body'))
        return typed_ast3.For(target=target, iter=iter_, body=body, orelse=[])

    def _loop_forall(self, node: ET.Element) -> typed_ast3.For:
        index_variables = node.find('./header/index-variables')
        outer_loop = None
        inner_loop = None
        for index_variable in index_variables.findall('./index-variable'):
            if not index_variable:
                continue # TODO: this is just a duct tape
            target, iter_ = self._index_variable(index_variable)
            if outer_loop is None:
                outer_loop = typed_ast3.For(target=target, iter=iter_, body=[], orelse=[])
                inner_loop = outer_loop
                continue
            inner_loop.body = [typed_ast3.For(target=target, iter=iter_, body=[], orelse=[])]
            inner_loop = inner_loop.body[0]
        inner_loop.body = self.transform_all_subnodes(node.find('./body'))
        return outer_loop

    def _index_variable(self, node: ET.Element) -> t.Tuple[typed_ast3.Name, typed_ast3.Call]:
        target = typed_ast3.Name(id=node.attrib['name'], ctx=typed_ast3.Load())
        lower_bound = node.find('./lower-bound')
        upper_bound = node.find('./upper-bound')
        step = node.find('./step')
        range_args = []
        if lower_bound is not None:
            args = self.transform_all_subnodes(lower_bound)
            assert len(args) == 1, args
            range_args.append(args[0])
        if upper_bound is not None:
            args = self.transform_all_subnodes(upper_bound)
            assert len(args) == 1, args
            range_args.append(typed_ast3.BinOp(
                left=args[0], op=typed_ast3.Add(), right=typed_ast3.Num(n=1)))
        if step is not None:
            args = self.transform_all_subnodes(step)
            assert len(args) == 1, args
            range_args.append(args[0])
        iter_ = typed_ast3.Call(
            func=typed_ast3.Name(id='range', ctx=typed_ast3.Load()),
            args=range_args, keywords=[])
        return target, iter_

    def _if(self, node: ET.Element):
        #_LOG.warning('if header:')
        header = self.transform_all_subnodes(node.find('./header'), warn=False)
        if len(header) != 1:
            _LOG.warning('%s', ET.tostring(node).decode().rstrip())
            _LOG.error([typed_astunparse.unparse(_).rstrip() for _ in header])
            raise NotImplementedError()
        #if len(header) == 0:
        #    test = typed_ast3.NameConstant(True)
        test = header[0]

        body = self.transform_all_subnodes(node.find('./body'))

        return typed_ast3.If(test=test, body=body, orelse=[])

    def _statement(self, node: ET.Element):
        details = self.transform_all_subnodes(node, warn=False)
        if len(details) == 1:
            if isinstance(details[0], typed_ast3.Assign):
                return details[0]
            return typed_ast3.Expr(value=details[0])
        #else:
        #    print(details)
        #    exit(1)
        args = []
        #args.append(typed_ast3.Str(s=ET.tostring(node).decode().rstrip())
        args.append(typed_ast3.Num(n=len(node)))
        return typed_ast3.Expr(value=typed_ast3.Call(
            func=typed_ast3.Name(id='print', ctx=typed_ast3.Load()),
            args=args, keywords=[]))

    def _call(self, node: ET.Element):
        called = self.transform_all_subnodes(node, warn=False)
        if len(called) != 1:
            _LOG.warning('%s', ET.tostring(node).decode().rstrip())
            _LOG.error([typed_astunparse.unparse(_).rstrip() for _ in called])
            raise SyntaxError("call statement must contain a single called object")
        if isinstance(called[0], typed_ast3.Call):
            return called[0]
        func = called[0]
        #assert name.tag == 'name' or name.
        args = []
        #args = node.findall('./name/subscripts/subscript') if isinstance(name, typed_ast3.Subscript) else []
        #exit(1)
        return typed_ast3.Call(func=func, args=args, keywords=[])

    def _assignment(self, node: ET.Element):
        target = self.transform_all_subnodes(node.find('./target'))
        value = self.transform_all_subnodes(node.find('./value'))
        #_LOG.warning('%s', ET.tostring(node).decode().rstrip())
        assert len(target) == 1, target
        assert len(value) == 1, value
        #raise NotImplementedError()
        return typed_ast3.Assign(targets=[target], value=value)

    def _operation(self, node: ET.Element) -> typed_ast3.AST:
        if node.attrib['type'] == 'binary':
            return self._operation_binary(node)
        _LOG.warning('%s', ET.tostring(node).decode().rstrip())
        raise NotImplementedError()

    def _operation_binary(self, node: ET.Element):
        operand_nodes = node.findall('./operand')
        #if len(operand_nodes) > 2:
        assert len(operand_nodes) >= 2, operand_nodes
        operands = []
        for operand in operand_nodes:
            new_operands = self.transform_all_subnodes(operand)
            if len(new_operands) != 1:
                _LOG.warning('%s', ET.tostring(operand).decode().rstrip())
                #_LOG.error("%s", operand)
                _LOG.error([typed_astunparse.unparse(_).rstrip() for _ in new_operands])
                raise SyntaxError()
            operands += new_operands
        #assert len(operand_nodes) == len(operands)
        assert len(operands) >= 2, operands
        operation_type, operator_type = self._operator(node.attrib['operator'])
        if operation_type is typed_ast3.Compare:
            if len(operands) != 2:
                raise NotImplementedError('exactly 2 operands expected in comparison operation')
            return typed_ast3.Compare(left=operands[0], ops=[operator_type()], comparators=[operands[1]])
        if operation_type is typed_ast3.BinOp:
            operation = typed_ast3.BinOp(left=operands[0], op=operator_type(), right=None)
            for operand in operands[1:-1]:
                operation.right = typed_ast3.BinOp(left=operand, op=operator_type(), right=None)
                operation = operation.right
            operation.right = operands[-1]
            return operation
            #return typed_ast3.BinOp(left=operands[0], op=operator_type(), right=operands[1])
        _LOG.warning('%s', ET.tostring(node).decode().rstrip())
        raise NotImplementedError(operation_type)

    def _operator(self, operator: str) -> t.Tuple[t.Type[typed_ast3.AST], t.Type[typed_ast3.AST]]:
        return {
            '+': (typed_ast3.BinOp, typed_ast3.Add),
            '-': (typed_ast3.BinOp, typed_ast3.Sub),
            '*': (typed_ast3.BinOp, typed_ast3.Mult),
            # missing: MatMult
            '/': (typed_ast3.BinOp, typed_ast3.Div),
            '%': (typed_ast3.BinOp, typed_ast3.Mod),
            '**': (typed_ast3.BinOp, typed_ast3.Pow),
            '//': (typed_ast3.BinOp, typed_ast3.Add), # concatenation operator, only in Fortran
            # LShift
            # RShift
            # BitOr
            # BitXor
            # BitAnd
            # missing: FloorDiv
            '==': (typed_ast3.Compare, typed_ast3.Eq),
            '/=': (typed_ast3.Compare, typed_ast3.NotEq),
            '<': (typed_ast3.Compare, typed_ast3.Lt),
            '<=': (typed_ast3.Compare, typed_ast3.LtE),
            '>': (typed_ast3.Compare, typed_ast3.Gt),
            '>=': (typed_ast3.Compare, typed_ast3.GtE),
            # Is
            # IsNot
            # In
            # NotIn
            }[operator]

    def _type(self, node: ET.Element) -> type:
        name = node.attrib['name']
        length = self.transform(node.find('./length')) if node.attrib['hasLength'] == "true" else None
        kind = self.transform(node.find('./kind')) if node.attrib['hasKind'] == "true" else None
        if name == 'character':
            if length is not None:
                pass
            return typed_ast3.parse('str', mode='eval')
        elif length is not None:
            return typed_ast3.parse({
                ('integer', 4): 'np.int32',
                ('integer', 8): 'np.int64',
                ('real', 4): 'np.float32',
                ('real', 8): 'np.float64'}[name, length], mode='eval')
        else:
            return typed_ast3.parse({
                'integer': 'int',
                'real': 'float'}[name], mode='eval')
        _LOG.warning('%s', ET.tostring(node).decode().rstrip())
        raise NotImplementedError()

    def _variable(self, node: ET.Element) -> t.Tuple[
            typed_ast3.Name, t.Any]:
        value_node = node.find('./initial-value')
        value = None
        if value_node is not None:
            values = self.transform_all_subnodes(value_node, warn=False)
            assert len(values) == 1, values
            value = values[0]
        return typed_ast3.Name(id=node.attrib['name']), value

    def _name(self, node: ET.Element) -> typed_ast3.AST:
        name = typed_ast3.Name(id=node.attrib['id'], ctx=typed_ast3.Load())
        name_type = node.attrib['type']
        subscripts_node = node.find('./subscripts')
        if name_type == "procedure":
            args = self._args(subscripts_node) if subscripts_node else []
            return typed_ast3.Call(func=name, args=args, keywords=[])
        if not subscripts_node:
            return name
        slice_ = self._subscripts(subscripts_node)
        return typed_ast3.Subscript(value=name, slice=slice_, ctx=typed_ast3.Load())

    def _args(self, node: ET.Element, arg_node_name: str = 'subscript') -> t.List[typed_ast3.AST]:
        args = []
        for arg_node in node.findall(f'./{arg_node_name}'):
            new_args = self.transform_all_subnodes(arg_node, warn=False)
            if not new_args:
                continue
            if len(new_args) != 1:
                _LOG.error('%s', ET.tostring(arg_node).decode().rstrip())
                _LOG.error('%s', [typed_astunparse.unparse(_) for _ in new_args])
                raise SyntaxError('args must be specified one new arg at a time')
            args += new_args
        return args

    def _subscripts(self, node: ET.Element) -> t.Union[typed_ast3.Index, typed_ast3.Slice]:
        subscripts = []
        for subscript in node.findall('./subscript'):
            new_subscripts = self.transform_all_subnodes(subscript, warn=False)
            if not new_subscripts:
                continue
            if len(new_subscripts) == 1:
                new_subscript = typed_ast3.Index(value=new_subscripts[0])
            elif len(new_subscripts) == 2:
                new_subscript = typed_ast3.Slice(
                    lower=new_subscripts[0], upper=new_subscripts[1], step=None)
            else:
                _LOG.error('%s', ET.tostring(subscript).decode().rstrip())
                _LOG.error('%s', [typed_astunparse.unparse(_) for _ in new_subscripts])
                raise SyntaxError('there must be 1 or 2 new subscript data elements')
            subscripts.append(new_subscript)
        if len(subscripts) == 1:
            return subscripts[0]
        elif len(subscripts) > 1:
            return typed_ast3.ExtSlice(dims=subscripts)
        raise SyntaxError('subscripts node must contain at least one "subscript" node')

    def _literal(self, node: ET.Element) -> t.Union[typed_ast3.Num, typed_ast3.Str]:
        if node.attrib['type'] == 'int':
            return typed_ast3.Num(n=int(node.attrib['value']))
        if node.attrib['type'] == 'char':
            assert len(node.attrib['value']) >= 2
            begin = node.attrib['value'][0]
            end = node.attrib['value'][-1]
            assert begin == end
            return typed_ast3.Str(s=node.attrib['value'][1:-1])
        _LOG.warning('%s', ET.tostring(node).decode().rstrip())
        raise NotImplementedError()


def transform(root_node: ET.Element) -> typed_ast3.AST:
    file_node = root_node[0]
    transformer = AstTransformer()
    return transformer.transform(file_node)
