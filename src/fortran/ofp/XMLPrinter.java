package fortran.ofp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.antlr.runtime.Token;
import org.apache.commons.cli.CommandLine;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import fortran.ofp.parser.java.FortranParserActionPrint;
import fortran.ofp.parser.java.IFortranParser;

/**
 * XML output generator for Open Fortran Parser.
 *
 * @author Mateusz Bysiek https://mbdevpl.github.io/
 */
public class XMLPrinter extends FortranParserActionPrint {

	/**
	 * Parsed command-line arguments.
	 */
	CommandLine cmd;

	/**
	 * Verbosity level from 0 to 100.
	 */
	int verbosity;

	/**
	 * XML document.
	 */
	protected Document doc;

	/**
	 * XML root node, the outermost open XML context.
	 */
	protected Element root;

	/**
	 * Current open XML context.
	 */
	protected Element context = null;

	public XMLPrinter(String[] args, IFortranParser parser, String filename) {
		super(args, parser, filename);

		// System.err.println(Arrays.toString(args));
		cmd = new ArgsParser().parse(args);
		verbosity = Integer.parseInt(cmd.getOptionValue("verbosity", "100"));
		if (verbosity >= 100) {
			setVerbose(true);
			setPrintKeywords(true);
		}

		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			doc = docBuilder.newDocument();
			root = contextOpen("ofp");
			setAttribute("version", "0.8.4");
			doc.appendChild(root);
		} catch (Exception error) {
			error.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Open a new XML context.
	 *
	 * @param name
	 */
	protected Element contextOpen(String name) {
		Element new_context = doc.createElement(name);
		if (context != null)
			context.appendChild(new_context);
		context = new_context;
		return context;
	}

	protected ArrayList<Element> contextHierarchy(Element context) {
		ArrayList<Element> hierarchy = new ArrayList<Element>();
		hierarchy.add(context);
		Element found = context;
		while (found != root && found.getParentNode() != null) {
			found = (Element) found.getParentNode();
			hierarchy.add(found);
		}
		return hierarchy;
	}

	protected ArrayList<Element> contextHierarchy() {
		return contextHierarchy(context);
	}

	protected ArrayList<String> contextNameHierarchy(Element context) {
		ArrayList<String> names = new ArrayList<String>();
		for (Element found : contextHierarchy(context))
			names.add(found.getTagName());
		return names;
	}

	protected ArrayList<String> contextNameHierarchy() {
		return contextNameHierarchy(context);
	}

	/**
	 * Try to find innermost open XML context with name equal to any of given names.
	 *
	 * @param names
	 * @return found context of null
	 */
	protected Element contextTryFind(String... names) {
		if (context == null)
			return null;
		Element found = context;
		List<String> names_list = Arrays.asList(names);
		while (!names_list.contains(found.getTagName())) {
			if (found == root)
				return null;
			found = (Element) found.getParentNode();
		}
		return found;
	}

	protected Element contextFind(String... names) {
		if (context == null)
			throw new NullPointerException("No open contexts, so " + Arrays.toString(names) + " cannot be found.");
		Element found = contextTryFind(names);
		if (found != null)
			return found;
		System.err.println("Cannot find any context of " + Arrays.toString(names) + " among open contexts.");
		System.err.println("Current context hierarchy (innermost first) is:");
		for (String name : contextNameHierarchy())
			System.err.println("  " + name);
		cleanUpAfterError();
		return null;
	}

	/**
	 * Rename given context to given name.
	 *
	 * @param context
	 * @param toName
	 */
	protected void contextRename(Element context, String toName) {
		doc.renameNode(context, null, toName);
	}

	protected void contextRename(String toName) {
		contextRename(context, context.getTagName(), toName);
	}

	/**
	 * Rename given context to given name after making sure about its current name.
	 *
	 * @param context
	 * @param fromName
	 * @param toName
	 */
	protected void contextRename(Element context, String fromName, String toName) {
		if (context.getTagName() != fromName)
			cleanUpAfterError("Cannot rename current context from '" + fromName + "' to '" + toName
					+ "' because it has unexpected name '" + context.getTagName() + "'.");
		contextRename(context, toName);
	}

	protected void contextRename(String fromName, String toName) {
		contextRename(context, fromName, toName);
	}

	/**
	 * Close the given XML context.
	 *
	 * @param name
	 */
	protected void contextClose(Element context) {
		if (context == root) {
			// if (context == null)
			// System.err.println("Cannot close given context because 'null' was given.");
			// else
			cleanUpAfterError("Cannot close given context because it is root node of the document.");
		}
		this.context = (Element) context.getParentNode();
	}

	protected void contextClose() {
		contextClose(context);
	}

	/**
	 * Close an innermost open XML context with name equal to any of given names.
	 *
	 * @param names
	 */
	protected void contextClose(String... names) {
		contextClose(contextFind(names));
	}

	/**
	 * Close all inner open XML contexts (if any) that are in first innermost context with name equal to any of given
	 * names.
	 *
	 * @param name
	 */
	protected void contextCloseAllInner(String... names) {
		context = contextFind(names);
	}

	/**
	 * Collection of attributes of given XML context.
	 *
	 * @param context the XML context to be queried
	 * @return list of attributes
	 */
	protected ArrayList<Attr> contextAttributes(Element context) {
		NamedNodeMap attributesMap = context.getAttributes();
		ArrayList<Attr> attributes = new ArrayList<Attr>();
		for (int i = 0; i < attributesMap.getLength(); i++)
			attributes.add((Attr) attributesMap.item(i));
		return attributes;
	}

	protected ArrayList<Attr> contextAttributes() {
		return contextAttributes(context);
	}

	/**
	 * Collection of children nodes of given XML context.
	 *
	 * @param context the XML context to be queried
	 * @param begin_index the index will be chosen from the end if negative number is given
	 * @param count number of results to return, return all results if zero is given
	 * @return list of nodes
	 */
	protected ArrayList<Element> contextNodes(Element context, int beginIndex, int count) {
		NodeList nodeList = context.getChildNodes();
		int nodeListLength = nodeList.getLength();
		ArrayList<Element> nodes = new ArrayList<Element>();
		// System.err.println("contextNodes of " + context + " " + beginIndex + " " + count);
		if (count == 0 && nodeListLength == 0)
			return nodes;
		if (beginIndex < 0)
			beginIndex = nodeListLength + beginIndex;
		if (beginIndex < 0 || beginIndex >= nodeListLength)
			throw new IndexOutOfBoundsException(
					"starting index " + beginIndex + " out of bounds [" + 0 + ", " + nodeListLength + ")");
		if (count == 0)
			count = nodeListLength - beginIndex;
		if (count < 0)
			throw new IndexOutOfBoundsException("attemted to return " + count + " number of nodes");
		int endIndex = beginIndex + count;
		/*
		System.err.println("returning " + count + " subnodes of " + context + " (from index " + beginIndex + " to "
				+ endIndex + ")");
		*/
		for (int i = beginIndex; i < endIndex; i++)
			nodes.add((Element) nodeList.item(i));
		return nodes;
	}

	protected ArrayList<Element> contextNodes(Element context) {
		return contextNodes(context, 0, 0);
	}

	protected ArrayList<Element> contextNodes(int beginIndex, int count) {
		return contextNodes(context, beginIndex, count);
	}

	protected ArrayList<Element> contextNodes() {
		return contextNodes(context, 0, 0);
	}

	protected int contextNodesCount(Element context) {
		return context.getChildNodes().getLength();
	}

	protected int contextNodesCount() {
		return contextNodesCount(context);
	}

	protected Element contextNode(Element context, int index) {
		return contextNodes(context, index, 1).get(0);
	}

	protected Element contextNode(int index) {
		return contextNode(context, index);
	}

	protected void contextPrint(Element context) {
		System.err.println(context);
		System.err.println(contextAttributes(context));
		System.err.println(contextNodes(context));
	}

	/**
	 * Set attribute for a given context.
	 *
	 * @param name
	 * @param value
	 * @param contextName
	 */
	protected void setAttribute(String name, Object value, Element context) {
		String valueString = null;
		if (value == null)
			valueString = "";
		else if (value instanceof Token)
			valueString = ((Token) value).getText();
		else
			valueString = value.toString();
		context.setAttribute(name, valueString);
	}

	protected void setAttribute(String name, Object value, String... names) {
		setAttribute(name, value, contextFind(names));
	}

	protected void setAttribute(String name, Object value) {
		setAttribute(name, value, context);
	}

	protected void moveTo(Element targetContext, Element element) {
		if (targetContext == element)
			cleanUpAfterError("Cannot move " + element + " to itself.");
		try {
			element.getParentNode().removeChild(element);
			targetContext.appendChild(element);
		} catch (org.w3c.dom.DOMException error) {
			System.err.println("Cannot move " + element + " to " + targetContext + ".");
			contextPrint(element);
			System.err.println(contextNameHierarchy(element));
			contextPrint(targetContext);
			System.err.println(contextNameHierarchy(targetContext));
			cleanUpAfterError(error);
		}
	}

	protected void moveTo(Element targetContext, ArrayList<Element> elements) {
		for (Element element : elements)
			moveTo(targetContext, element);
	}

	protected void moveHere(Element element) {
		moveTo(context, element);
	}

	protected void moveHere(ArrayList<Element> elements) {
		moveTo(context, elements);
	}

	protected void printRuleHeader(int rule, String name, String addendum) {
		contextOpen(name);
		setAttribute("rule", rule);
		if (addendum.length() > 0)
			setAttribute("addendum", addendum);
	}

	protected void printRuleTrailer() {
		contextClose();
	}

	protected void printParameter(Object param, String name) {
		setAttribute(name, param);
	}

	protected void printParameter(Token param, String name) {
		setAttribute(name, param);
	}

	public void generic_name_list_part(Token id) {
		contextOpen("name");
		setAttribute("id", id);
		if (verbosity >= 100)
			super.generic_name_list_part(id);
		contextClose();
	}

	public void generic_name_list__begin() {
		if (context.getTagName().equals("specification") || context.getTagName().equals("file"))
			contextOpen("declaration");
		contextOpen("names");
		if (verbosity >= 100)
			super.generic_name_list__begin();
	}

	public void generic_name_list(int count) {
		if (verbosity >= 100)
			super.generic_name_list(count);
		setAttribute("count", count);
		contextClose("names");
	}

	public void specification_part(int numUseStmts, int numImportStmts, int numImplStmts, int numDeclConstructs) {
		if (context.getTagName().equals("header")) {
			contextClose("header");
			contextOpen("body");
		}
		if (context.getTagName().equals("declaration"))
			contextClose("declaration");
		if (!context.getTagName().equals("specification"))
			contextOpen("specification");
		contextCloseAllInner("specification");
		if (verbosity >= 80)
			super.specification_part(numUseStmts, numImportStmts, numImplStmts, numDeclConstructs);
		setAttribute("uses", numUseStmts);
		setAttribute("imports", numImportStmts);
		setAttribute("implicits", numImplStmts);
		setAttribute("declarations", numDeclConstructs);
		contextClose();
		contextOpen("statement");
	}

	public void declaration_construct() {
		contextClose("declaration");
		if (verbosity >= 100)
			super.declaration_construct();
		contextOpen("declaration");
	}

	public void execution_part_construct() {
		if (verbosity >= 100)
			super.execution_part_construct();
	}

	public void specification_stmt() {
		if (verbosity >= 100)
			super.specification_stmt();
	}

	public void executable_construct() {
		if (verbosity >= 100)
			super.executable_construct();
	}

	public void action_stmt() {
		if (contextTryFind("statement") == null) {
			// TODO this ugly workaround should be removed
			contextClose();
			Element element = contextNode(-1);
			contextOpen("statement");
			moveHere(element);
		}
		if (verbosity >= 100)
			super.action_stmt();
		contextClose("statement");
		contextOpen("statement");
	}

	public void keyword() {
		if (verbosity >= 100)
			super.keyword();
	}

	public void name(Token id) {
		super.name(id);
	}

	public void constant(Token id) {
		super.constant(id);
	}

	public void scalar_constant() {
		if (verbosity >= 100)
			super.scalar_constant();
	}

	public void literal_constant() {
		if (verbosity >= 100)
			super.literal_constant();
		contextClose("literal");
	}

	public void label(Token lbl) {
		boolean closedLoop = false;
		Element outerContext = context;
		while (outerContext != root) {
			if (outerContext.getTagName().equals("loop") && outerContext.getAttribute("label").equals(lbl.getText())) {
				context = outerContext;
				closedLoop = true;
				break;
			}
			outerContext = (Element) outerContext.getParentNode();
		}
		super.label(lbl);
		if (closedLoop)
			contextOpen("statement");
	}

	public void intrinsic_type_spec(Token keyword1, Token keyword2, int type, boolean hasKindSelector) {
		if (!context.getTagName().equals("declaration")) {
			// TODO: ensure being in body
			contextOpen("declaration");
		}
		setAttribute("type", "variable");
		super.intrinsic_type_spec(keyword1, keyword2, type, hasKindSelector);
	}

	public void kind_selector(Token token1, Token token2, boolean hasExpression) {
		if (hasExpression) {
			Element value = contextNode(-1);
			contextOpen("kind");
			moveHere(value);
		} else {
			contextOpen("kind");
			setAttribute("value", token2);
		}
		super.kind_selector(token1, token2, hasExpression);
		contextClose();
	}

	public void int_literal_constant(Token digitString, Token kindParam) {
		contextOpen("literal");
		setAttribute("type", "int");
		setAttribute("value", digitString);
		super.int_literal_constant(digitString, kindParam);
	}

	public void boz_literal_constant(Token constant) {
		contextOpen("literal");
		setAttribute("type", "int");
		setAttribute("value", constant);
		super.boz_literal_constant(constant);
	}

	public void real_literal_constant(Token realConstant, Token kindParam) {
		contextOpen("literal");
		setAttribute("type", "real");
		setAttribute("value", realConstant);
		super.real_literal_constant(realConstant, kindParam);
	}

	public void char_length(boolean hasTypeParamValue) {
		Element value = contextNode(-1);
		contextOpen("length");
		moveHere(value);
		super.char_length(hasTypeParamValue);
		contextClose();
	}

	public void scalar_int_literal_constant() {
		if (verbosity >= 100)
			super.scalar_int_literal_constant();
		contextClose("literal");
	}

	public void char_literal_constant(Token digitString, Token id, Token str) {
		contextOpen("literal");
		setAttribute("type", "char");
		setAttribute("value", str);
		super.char_literal_constant(digitString, id, str);
	}

	public void logical_literal_constant(Token logicalValue, boolean isTrue, Token kindParam) {
		contextOpen("literal");
		setAttribute("type", "bool");
		setAttribute("value", isTrue);
		super.logical_literal_constant(logicalValue, isTrue, kindParam);
	}

	public void derived_type_stmt(Token label, Token keyword, Token id, Token eos, boolean hasTypeAttrSpecList,
			boolean hasGenericNameList) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "type");
		super.derived_type_stmt(label, keyword, id, eos, hasTypeAttrSpecList, hasGenericNameList);
	}

	public void derived_type_spec(Token typeName, boolean hasTypeParamSpecList) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "variable");
		super.derived_type_spec(typeName, hasTypeParamSpecList);
	}

	public void array_constructor() {
		context = contextNode(-1); // temporarily reopen previously-closed context
		if (verbosity >= 100)
			super.array_constructor();
		contextClose(); // re-close previously closed context
	}

	public void ac_spec() {
		context = contextNode(-1); // temporarily reopen previously-closed context
		if (verbosity >= 100)
			super.ac_spec();
		contextClose(); // re-close previously closed context
	}

	public void ac_value() {
		contextClose("value");
		if (verbosity >= 100)
			super.ac_value();
		contextOpen("value");
	}

	public void ac_value_list__begin() {
		contextOpen("array-constructor-values");
		if (verbosity >= 100)
			super.ac_value_list__begin();
		contextOpen("value");
	}

	public void ac_value_list(int count) {
		contextClose("value");
		contextCloseAllInner("array-constructor-values", "array-constructor");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.ac_value_list(count);
		contextClose();
	}

	public void ac_implied_do() {
		Element value = context;
		contextClose();
		contextClose("value");
		moveHere(value);
		super.ac_implied_do();
		contextRename("array-constructor-values", "array-constructor");
		contextOpen("value");
	}

	public void ac_implied_do_control(boolean hasStride) {
		contextClose("index-variable");
		super.ac_implied_do_control(hasStride);
	}

	public void type_declaration_stmt(Token label, int numAttributes, Token eos) {
		super.type_declaration_stmt(label, numAttributes, eos);
	}

	public void declaration_type_spec(Token udtKeyword, int type) {
		ArrayList<Element> typeDeclarations = contextNodes();
		contextOpen("type");
		setAttribute("hasLength", false);
		setAttribute("hasKind", false);
		for (Element declaration : typeDeclarations) {
			switch (declaration.getTagName()) {
			case "intrinsic-type-spec":
				setAttribute("name", declaration.getAttribute("keyword1"));
				break;
			case "length":
				setAttribute("hasLength", true);
				break;
			case "kind":
				setAttribute("hasKind", true);
				break;
			default:
				break;
			}
			moveHere(declaration);
		}
		super.declaration_type_spec(udtKeyword, type);
		contextClose();
	}

	public void entity_decl(Token id, boolean hasArraySpec, boolean hasCoarraySpec, boolean hasCharLength,
			boolean hasInitialization) {
		contextCloseAllInner("variable");
		super.entity_decl(id, hasArraySpec, hasCoarraySpec, hasCharLength, hasInitialization);
		setAttribute("name", id);
		setAttribute("hasInitialValue", hasInitialization);
		contextClose();
		contextOpen("variable");
	}

	public void entity_decl_list__begin() {
		contextOpen("variables");
		if (verbosity >= 100)
			super.entity_decl_list__begin();
		contextOpen("variable");
	}

	public void entity_decl_list(int count) {
		contextClose("variable");
		contextCloseAllInner("variables");
		if (verbosity >= 100)
			super.entity_decl_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void initialization(boolean hasExpr, boolean hasNullInit) {
		Element initialValue = contextNode(-1);
		contextOpen("initial-value");
		moveHere(initialValue);
		super.initialization(hasExpr, hasNullInit);
		contextClose();
	}

	public void access_spec(Token keyword, int type) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		super.access_spec(keyword, type);
	}

	public void language_binding_spec(Token keyword, Token id, boolean hasName) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		super.language_binding_spec(keyword, id, hasName);
	}

	public void array_spec(int count) {
		contextCloseAllInner("dimensions");
		if (verbosity >= 100)
			super.array_spec(count);
		setAttribute("count", count);
		contextClose();
	}

	public void array_spec_element(int type) {
		Element value = null;
		Element value2 = null;
		switch (type) {
		case 702:
			value2 = contextNode(-2);
		case 700:
		case 701:
		case 703:
			value = contextNode(-1);
			break;
		case 704:
		case 705:
			break;
		default:
			throw new IllegalArgumentException(Integer.toString(type));
		}

		if (!context.getTagName().equals("dimensions"))
			contextOpen("dimensions");
		contextOpen("dimension");

		switch (type) {
		case 700:
			setAttribute("type", "simple"); // (a)
			moveHere(value);
			break;
		case 701:
			setAttribute("type", "upper-bound-assumed-shape"); // (a:)
			moveHere(value);
			break;
		case 702:
			setAttribute("type", "range"); // (a:b)
			contextOpen("range");
			contextOpen("lower-bound");
			moveHere(value2);
			contextClose();
			contextOpen("upper-bound");
			moveHere(value);
			contextClose();
			contextClose();
			break;
		case 703:
			setAttribute("type", "upper-bound-assumed-size"); // (a:*)
			moveHere(value);
			break;
		case 704:
			setAttribute("type", "assumed-size"); // (*)
			break;
		case 705:
			setAttribute("type", "assumed-shape"); // (:)
			break;
		default:
			throw new IllegalArgumentException(Integer.toString(type));
		}
		super.array_spec_element(type);
		contextClose();
	}

	public void access_id_list__begin() {
		// contextOpen("access-list");
		if (verbosity >= 100)
			super.access_id_list__begin();
	}

	public void access_id_list(int count) {
		super.access_id_list(count);
		// contextClose("access-list");
	}

	public void allocatable_decl_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "allocatables");
		super.allocatable_decl_list__begin();
	}

	public void asynchronous_stmt(Token label, Token keyword, Token eos) {
		if (!context.getTagName().equals("declaration")) {
			Element value = contextNode(-1);
			if (value.getTagName() != "names")
				cleanUpAfterError("tag name is not 'names' but '" + value.getTagName() + "'");
			contextOpen("declaration");
			moveHere(value);
		}
		super.asynchronous_stmt(label, keyword, eos);
	}

	public void codimension_decl_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "codimensions");
		super.codimension_decl_list__begin();
	}

	public void data_stmt_object_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "data");
		contextOpen("variables");
		if (verbosity >= 100)
			super.data_stmt_object_list__begin();
	}

	public void data_stmt_object_list(int count) {
		if (verbosity >= 100)
			super.data_stmt_object_list(count);
		setAttribute("count", count);
		contextClose("variables");
	}

	public void data_stmt_value_list__begin() {
		contextOpen("values");
		if (verbosity >= 100)
			super.data_stmt_value_list__begin();
	}

	public void data_stmt_value_list(int count) {
		if (verbosity >= 100)
			super.data_stmt_value_list(count);
		setAttribute("count", count);
		contextClose("values");
	}

	public void hollerith_literal_constant(Token hollerithConstant) {
		contextOpen("literal");
		setAttribute("type", "hollerith");
		setAttribute("value", hollerithConstant);
		super.hollerith_literal_constant(hollerithConstant);
	}

	public void dimension_stmt(Token label, Token keyword, Token eos, int count) {
		contextCloseAllInner("variables");
		setAttribute("count", count);
		super.dimension_stmt(label, keyword, eos, count);
		contextClose();
		setAttribute("type", "variable-dimensions");
	}

	public void dimension_decl(Token id) {
		Element value = contextNode(-1);
		if (!context.getTagName().equals("variables")) {
			if (!context.getTagName().equals("declaration"))
				contextOpen("declaration");
			contextOpen("variables");
		}
		contextOpen("variable");
		setAttribute("name", id);
		moveHere(value);
		/*
		if (contextTryFind("declaration") == null) {
			contextOpen("declaration");
			setAttribute("type", "dimension");
		}
		*/
		super.dimension_decl(id);
		contextClose("variable");
	}

	public void named_constant_def_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "parameter");
		if (verbosity >= 100)
			super.named_constant_def_list__begin();
	}

	public void pointer_decl_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		super.pointer_decl_list__begin();
	}

	public void save_stmt(Token label, Token keyword, Token eos, boolean hasSavedEntityList) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		super.save_stmt(label, keyword, eos, hasSavedEntityList);
	}

	public void target_decl_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "targets");
		if (verbosity >= 100)
			super.target_decl_list__begin();
	}

	public void target_decl_list(int count) {
		// TODO Auto-generated method stub
		super.target_decl_list(count);
	}

	public void value_stmt(Token label, Token keyword, Token eos) {
		// TODO: get also label node if there is one
		Element value = contextNode(-1);
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "value");
		moveHere(value);
		super.value_stmt(label, keyword, eos);
	}

	public void volatile_stmt(Token label, Token keyword, Token eos) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "volatile");
		super.volatile_stmt(label, keyword, eos);
	}

	public void implicit_stmt(Token label, Token implicitKeyword, Token noneKeyword, Token eos,
			boolean hasImplicitSpecList) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		if (verbosity >= 20)
			super.implicit_stmt(label, implicitKeyword, noneKeyword, eos, hasImplicitSpecList);
		setAttribute("type", "implicit");
		setAttribute("subtype", noneKeyword == null ? "some" : "none");
		contextClose("declaration");
		contextOpen("declaration");
	}

	public void namelist_stmt(Token label, Token keyword, Token eos, int count) {
		contextCloseAllInner("namelists");
		super.namelist_stmt(label, keyword, eos, count);
		setAttribute("count", count);
	}

	public void namelist_group_name(Token id) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "namelists");
		contextOpen("namelists");
		contextOpen("names");
		if (verbosity >= 100)
			super.namelist_group_name(id);
		setAttribute("id", id);
	}

	public void namelist_group_object_list(int count) {
		contextCloseAllInner("names");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.namelist_group_object_list(count);
		contextClose();
	}

	public void equivalence_set_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "equivalence");
		contextOpen("equivalents");
		if (verbosity >= 100)
			super.equivalence_set_list__begin();
		contextOpen("equivalent");
	}

	public void equivalence_set_list(int count) {
		contextClose("equivalent");
		contextCloseAllInner("equivalents");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.equivalence_set_list(count);
		contextClose();
	}

	public void equivalence_object() {
		contextClose("equivalent");
		if (verbosity >= 100)
			super.equivalence_object();
		contextOpen("equivalent");
	}

	public void equivalence_object_list__begin() {
		// TODO Auto-generated method stub
		super.equivalence_object_list__begin();
	}

	public void equivalence_object_list(int count) {
		// TODO Auto-generated method stub
		super.equivalence_object_list(count);
	}

	public void common_block_name(Token id) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "common");
		super.common_block_name(id);
	}

	public void common_block_object_list__begin() {
		contextOpen("objects");
		if (verbosity >= 100)
			super.common_block_object_list__begin();
	}

	public void common_block_object_list(int count) {
		contextCloseAllInner("objects");
		if (verbosity >= 100)
			super.common_block_object_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void variable() {
		if (verbosity >= 100)
			super.variable();
		setAttribute("type", "variable");
		contextClose("name");
	}

	public void designator_or_func_ref() {
		if (verbosity >= 100)
			super.designator_or_func_ref();
		setAttribute("type", "ambiguous");
		contextClose("name");
	}

	public void substring_range(boolean hasLowerBound, boolean hasUpperBound) {
		Element lowerBound = null;
		Element upperBound = null;
		if (hasLowerBound)
			lowerBound = contextNode(-1);
		if (hasUpperBound) {
			upperBound = lowerBound;
			if (hasLowerBound)
				lowerBound = contextNode(-2);
			else
				lowerBound = null;
		}
		contextOpen("name");
		contextOpen("range");
		if (lowerBound != null) {
			contextOpen("lower-bound");
			moveHere(lowerBound);
			contextClose();
		}
		if (upperBound != null) {
			contextOpen("upper-bound");
			moveHere(upperBound);
			contextClose();
		}
		if (verbosity >= 100)
			super.substring_range(hasLowerBound, hasUpperBound);
		contextClose();
	}

	public void part_ref(Token id, boolean hasSectionSubscriptList, boolean hasImageSelector) {
		Element e = null;
		if (hasSectionSubscriptList) {
			e = contextNode(-1);
			if (!e.getTagName().equals("subscripts"))
				cleanUpAfterError("tag name is not 'subscripts' but '" + e.getTagName() + "'");
		}
		contextOpen("name");
		setAttribute("id", id);
		setAttribute("hasSubscripts", hasSectionSubscriptList);
		if (hasSectionSubscriptList)
			moveHere(e);
		if (verbosity >= 60)
			super.part_ref(id, hasSectionSubscriptList, hasImageSelector);
	}

	public void section_subscript(boolean hasLowerBound, boolean hasUpperBound, boolean hasStride,
			boolean isAmbiguous) {
		contextCloseAllInner("subscript");
		if (!hasLowerBound && !hasUpperBound && !hasStride)
			setAttribute("type", "empty");
		else if (hasLowerBound && !hasUpperBound && !hasStride)
			setAttribute("type", "simple");
		else {
			setAttribute("type", "range");
			Element outerContext = context;
			Element lowerBound = null;
			Element upperBound = null;
			Element step = null;
			contextOpen("range");
			if (hasLowerBound) {
				contextOpen("lower-bound");
				lowerBound = context;
				contextClose();
			}
			if (hasUpperBound) {
				contextOpen("upper-bound");
				upperBound = context;
				contextClose();
			}
			if (hasStride) {
				contextOpen("step");
				step = context;
				contextClose();
			}
			contextClose();
			if (hasStride) {
				Element value = contextNode(outerContext, -2);
				moveTo(step, value);
			}
			if (hasUpperBound) {
				Element value = contextNode(outerContext, -2);
				moveTo(upperBound, value);
			}
			if (hasLowerBound) {
				Element value = contextNode(outerContext, -2);
				moveTo(lowerBound, value);
			}
		}
		contextClose();
		if (verbosity >= 80)
			super.section_subscript(hasLowerBound, hasUpperBound, hasStride, isAmbiguous);
		contextOpen("subscript");
	}

	public void section_subscript_list__begin() {
		contextOpen("subscripts");
		if (verbosity >= 100)
			super.section_subscript_list__begin();
		contextOpen("subscript");
	}

	public void section_subscript_list(int count) {
		contextClose("subscript");
		if (verbosity >= 100)
			super.section_subscript_list(count);
		setAttribute("count", count);
		contextClose("subscripts");
	}

	public void allocate_stmt(Token label, Token allocateKeyword, Token eos, boolean hasTypeSpec,
			boolean hasAllocOptList) {
		/*
		if (hasAllocOptList)
			cleanUpAfterError("didn't expect hasAllocOptList=" + hasAllocOptList);
		*/
		int movedCount = 1 + (hasAllocOptList ? 1 : 0);
		ArrayList<Element> elements = contextNodes(-movedCount, movedCount);
		contextOpen("allocate");
		moveHere(elements);
		super.allocate_stmt(label, allocateKeyword, eos, hasTypeSpec, hasAllocOptList);
		contextClose();
	}

	public void alloc_opt(Token allocOpt) {
		contextCloseAllInner("keyword-arguments");
		Element element = contextNode(-1);
		contextOpen("keyword-argument");
		setAttribute("name", allocOpt);
		moveHere(element);
		if (verbosity >= 100)
			super.alloc_opt(allocOpt);
		contextClose();
	}

	public void alloc_opt_list__begin() {
		contextOpen("keyword-arguments");
		if (verbosity >= 100)
			super.alloc_opt_list__begin();
	}

	public void alloc_opt_list(int count) {
		contextCloseAllInner("keyword-arguments");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.alloc_opt_list(count);
		contextClose();
	}

	public void allocation(boolean hasAllocateShapeSpecList, boolean hasAllocateCoarraySpec) {
		if (hasAllocateShapeSpecList || hasAllocateCoarraySpec)
			cleanUpAfterError("didn't expect hasAllocateShapeSpecList=" + hasAllocateShapeSpecList
					+ " hasAllocateCoarraySpec=" + hasAllocateCoarraySpec);
		Element element = contextNode(-1);
		if (element.getTagName().equals("expression"))
			context = contextNode(-1);
		else {
			contextOpen("expression");
			moveHere(element);
		}
		super.allocation(hasAllocateShapeSpecList, hasAllocateCoarraySpec);
		contextClose();
	}

	public void allocation_list__begin() {
		contextOpen("expressions");
		if (verbosity >= 100)
			super.allocation_list__begin();
	}

	public void allocation_list(int count) {
		contextCloseAllInner("expressions");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.allocation_list(count);
		contextClose();
	}

	public void allocate_object() {
		setAttribute("type", "variable");
		contextClose("name");
		Element element = contextNode(-1);
		contextOpen("expression");
		moveHere(element);
		if (verbosity >= 100)
			super.allocate_object();
		contextClose();
	}

	public void allocate_object_list__begin() {
		contextOpen("expressions");
		if (verbosity >= 100)
			super.allocate_object_list__begin();
	}

	public void allocate_object_list(int count) {
		contextCloseAllInner("expressions");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.allocate_object_list(count);
		contextClose();
	}

	public void deallocate_stmt(Token label, Token deallocateKeyword, Token eos, boolean hasDeallocOptList) {
		Element element2 = hasDeallocOptList ? contextNode(-2) : null;
		Element element = contextNode(-1);
		contextOpen("deallocate");
		if (hasDeallocOptList)
			moveHere(element2);
		moveHere(element);
		super.deallocate_stmt(label, deallocateKeyword, eos, hasDeallocOptList);
		contextClose();
	}

	public void dealloc_opt(Token id) {
		contextCloseAllInner("keyword-arguments");
		Element element = contextNode(-1);
		contextOpen("keyword-argument");
		setAttribute("name", id);
		moveHere(element);
		if (verbosity >= 100)
			super.dealloc_opt(id);
		contextClose();
	}

	public void dealloc_opt_list__begin() {
		contextOpen("keyword-arguments");
		if (verbosity >= 100)
			super.dealloc_opt_list__begin();
	}

	public void dealloc_opt_list(int count) {
		contextCloseAllInner("keyword-arguments");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.dealloc_opt_list(count);
		contextClose();
	}

	public void primary() {
		context = contextNode(-1); // temporarily reopen previously-closed context
		if (verbosity >= 100)
			super.primary();
		contextClose(); // re-close previously closed context
		if (context.getTagName().equals("index-variable")) {
			// TODO remove this ugly workaround
			ArrayList<Element> indexVariableNodes = contextNodes();
			boolean hasLowerBound = false;
			boolean hasUpperBound = false;
			boolean hasStep = false;
			ArrayList<Element> unassignedNodes = new ArrayList<Element>();
			for (Element node : indexVariableNodes) {
				if (node.getTagName().equals("lower-bound"))
					hasLowerBound = true;
				else if (node.getTagName().equals("upper-bound"))
					hasUpperBound = true;
				else if (node.getTagName().equals("step"))
					hasStep = true;
				else
					unassignedNodes.add(node);
			}
			if (unassignedNodes.size() > 0) {
				if (!hasLowerBound)
					contextOpen("lower-bound");
				else if (!hasUpperBound)
					contextOpen("upper-bound");
				else if (!hasStep)
					contextOpen("step");
				moveHere(unassignedNodes);
				contextClose();
			}
		}
	}

	public void parenthesized_expr() {
		context = contextNode(-1); // temporarily reopen previously-closed context
		if (verbosity >= 100)
			super.parenthesized_expr();
		contextClose(); // re-close previously closed context
	}

	protected void genericMultiaryOperatorIntro() {
		int nodesCount = contextNodesCount();
		if (nodesCount == 0)
			return;
		Element previousContext = contextNode(-1);
		contextOpen("operation");
		setAttribute("type", "multiary");
		contextOpen("operand");
		moveHere(previousContext);
		contextClose();
	}

	protected void genericMultiaryOperatorOutro() {
		if (context.getTagName().equals("operation"))
			contextOpen("operand");
	}

	public void power_operand(boolean hasPowerOperand) {
		if (verbosity >= 100)
			super.power_operand(hasPowerOperand);
		if (!hasPowerOperand)
			return;
		contextClose("operation");
	}

	public void power_operand__power_op(Token powerOp) {
		contextClose("operand");
		if (verbosity >= 100)
			super.power_operand__power_op(powerOp);
		if (!context.getTagName().equals("operation"))
			cleanUpAfterError("current context is not 'operation' but '" + context.getTagName() + "'");
		setAttribute("operator", powerOp);
	}

	public void mult_operand(int numMultOps) {
		if (verbosity >= 100)
			super.mult_operand(numMultOps);
		if (numMultOps == 0)
			return;
		contextClose("operation");
	}

	public void mult_operand__mult_op(Token multOp) {
		contextClose("operand");
		// contextOpen("operator");
		if (verbosity >= 100)
			super.mult_operand__mult_op(multOp);
		if (!context.getTagName().equals("operation"))
			cleanUpAfterError("current context is not 'operation' but '" + context.getTagName() + "'");
		if (multOp == null)
			throw new NullPointerException();
		if (context.hasAttribute("operator") && !context.getAttribute("operator").equals(multOp.getText()))
			throw new RuntimeException("blah blah" + context.getAttribute("operator"));
		// setAttribute("operator", multOp);
	}

	public void signed_operand(Token addOp) {
		Element operationContext = null;
		ArrayList<Element> operands = null;
		if (addOp != null) {
			if (!context.getTagName().equals("operand"))
				cleanUpAfterError("current context is not 'operand' but '" + context.getTagName() + "'");
			Element operandContext = context;
			contextClose("operand");
			operationContext = context;
			if (context.getAttribute("type").equals("multiary")) {
				// fix unary operation incorrectly classified as multiary
				setAttribute("type", "unary");
				ArrayList<Element> operationNodes = contextNodes();
				operands = new ArrayList<Element>();
				for (Element node : operationNodes)
					if (node != operandContext && node.getTagName().equals("operand"))
						operands.add(node);
				if (operands.size() != 1)
					cleanUpAfterError("exactly 1 operand expected but " + operands.size() + " present");
			}
		}
		if (verbosity >= 100)
			super.signed_operand(addOp);
		if (addOp != null) {
			contextClose("operation");
			if (operands != null)
				for (Element operand : operands) {
					operationContext.removeChild(operand);
					for (Element operandExpr : contextNodes(operand))
						context.insertBefore(operandExpr, operationContext);
				}
		}
	}

	public void add_operand(int numAddOps) {
		if (verbosity >= 100)
			super.add_operand(numAddOps);
		if (numAddOps > 0)
			contextClose("operation");
	}

	public void add_operand__add_op(Token addOp) {
		contextClose("operand");
		if (verbosity >= 100)
			super.add_operand__add_op(addOp);
	}

	public void level_2_expr(int numConcatOps) {
		if (numConcatOps > 0) {
			contextClose("operand");
			contextCloseAllInner("operation");
		}
		if (verbosity >= 100)
			super.level_2_expr(numConcatOps);
		if (numConcatOps > 0) {
			setAttribute("operators", numConcatOps);
			contextClose();
		}
	}

	public void power_op(Token powerKeyword) {
		Element previousContext = contextNode(-1);
		contextOpen("operation");
		setAttribute("type", "multiary");
		// setAttribute("operator", powerKeyword);
		contextOpen("operand");
		moveHere(previousContext);
		contextClose();
		contextOpen("operator");
		setAttribute("operator", powerKeyword);
		if (verbosity >= 100)
			super.power_op(powerKeyword);
		contextClose();
		contextOpen("operand");
	}

	public void mult_op(Token multKeyword) {
		if (context.getTagName().equals("operation")) {
			// TODO
		} else {
			if (contextNodesCount() == 0) {
				contextOpen("operation");
				setAttribute("type", "unary");
			} else {
				Element previousContext = contextNode(-1);
				contextOpen("operation");
				setAttribute("type", "multiary");
				contextOpen("operand");
				moveHere(previousContext);
			}
		}
		if (context.getTagName().equals("operand"))
			contextClose("operand");
		contextOpen("operator");
		setAttribute("operator", multKeyword);
		if (verbosity >= 100)
			super.mult_op(multKeyword);
		contextClose();
		contextOpen("operand");
	}

	public void add_op(Token addKeyword) {
		if (context.getTagName().equals("operation")) {
			// TODO
		} else {
			if (contextNodesCount() == 0) {
				contextOpen("operation");
				setAttribute("type", "unary");
			} else {
				Element previousContext = contextNode(-1);
				contextOpen("operation");
				setAttribute("type", "multiary");
				contextOpen("operand");
				moveHere(previousContext);
			}
		}
		if (context.getTagName().equals("operand"))
			contextClose("operand");
		contextOpen("operator");
		setAttribute("operator", addKeyword);
		if (verbosity >= 100)
			super.add_op(addKeyword);
		contextClose();
		contextOpen("operand");
	}

	public void level_3_expr(Token relOp) {
		if (relOp != null)
			contextClose("operand");
		if (verbosity >= 80)
			super.level_3_expr(relOp);
		if (relOp != null) {
			// setAttribute("operator", relOp);
			setAttribute("operators", 1);
			contextClose("operation");
		}
	}

	public void concat_op(Token concatKeyword) {
		if (context.getTagName().equals("operand"))
			contextClose("operand");
		if (context.getTagName().equals("operation")) {
			// TODO
		} else {
			Element previousContext = contextNode(-1);
			contextOpen("operation");
			setAttribute("type", "multiary");
			contextOpen("operand");
			moveHere(previousContext);
			contextClose("operand");
		}
		contextOpen("operator");
		if (verbosity >= 100)
			super.concat_op(concatKeyword);
		setAttribute("operator", "//");
		contextClose();
		contextOpen("operand");
	}

	public void rel_op(Token relOp) {
		genericMultiaryOperatorIntro();
		contextOpen("operator");
		setAttribute("operator", relOp);
		if (verbosity >= 100)
			super.rel_op(relOp);
		contextClose();
		genericMultiaryOperatorOutro();
	}

	public void and_operand(boolean hasNotOp, int numAndOps) {
		if (hasNotOp) {
			if (numAndOps == 0 && !context.getTagName().equals("operand")) {
				Element previousContext = contextNode(-1);
				contextOpen("operand");
				moveHere(previousContext);
			}
			contextClose("operand");
		}
		if (numAndOps > 0 || hasNotOp)
			contextCloseAllInner("operation");
		if (verbosity >= 100)
			super.and_operand(hasNotOp, numAndOps);
		if (numAndOps > 0) {
			ArrayList<Element> nodes = contextNodes();
			ArrayList<Integer> operators = new ArrayList<Integer>();
			int i = 0;
			for (Element node : nodes) {
				if (node.getTagName().equals("operator"))
					operators.add(i);
				i += 1;
			}
			if (numAndOps != operators.size()) {
				int firstRelevantOp = operators.size() - numAndOps;
				if (firstRelevantOp < 1)
					cleanUpAfterError(
							"sorry, numAndOps != operands.size()... " + numAndOps + " != " + operators.size());
				int startIndex = operators.get(firstRelevantOp).intValue() - 1;
				nodes = contextNodes(startIndex, nodes.size() - startIndex);
				contextOpen("operation");
				moveHere(nodes);
				/*
				for (int j = startIndex; j < nodes.size(); j++) {
					Element node = nodes.get(j);
					moveHere(node);
				}
				*/
				// throw new IllegalArgumentException(
				// "sorry, numAndOps != operands.size()... " + numAndOps + " != " + operators.size());
			}
		}
		if (numAndOps > 0 || hasNotOp)
			contextClose();
	}

	public void and_operand__not_op(boolean hasNotOp) {
		if (hasNotOp)
			contextClose("operand");
		super.and_operand__not_op(hasNotOp);
		// cleanUpAfterError("hasNotOp = " + hasNotOp);
	}

	public void or_operand(int numOrOps) {
		if (numOrOps > 0) {
			if (!context.getTagName().equals("operand")) {
				Element previousContext = contextNode(-1);
				contextOpen("operand");
				moveHere(previousContext);
			}
			contextClose("operand");
		}
		if (verbosity >= 100)
			super.or_operand(numOrOps);
		if (numOrOps > 0)
			contextClose("operation");
	}

	public void not_op(Token notOp) {
		contextOpen("operation");
		setAttribute("type", "unary");
		contextOpen("operator");
		setAttribute("operator", notOp);
		if (verbosity >= 100)
			super.not_op(notOp);
		contextClose();
		contextOpen("operand");
	}

	public void and_op(Token andOp) {
		if (context.getTagName().equals("operand"))
			contextClose("operand");
		if (context.getTagName().equals("operation")) {
			// TODO
		} else {
			Element previousContext = contextNode(-1);
			contextOpen("operation");
			setAttribute("type", "multiary");
			contextOpen("operand");
			moveHere(previousContext);
			contextClose();
		}
		contextOpen("operator");
		setAttribute("operator", andOp);
		if (verbosity >= 100)
			super.and_op(andOp);
		contextClose();
		contextOpen("operand");
	}

	public void or_op(Token orOp) {
		if (context.getTagName().equals("operand"))
			contextClose("operand");
		if (context.getTagName().equals("operation")) {
			// TODO
		} else {
			Element previousContext = contextNode(-1);
			contextOpen("operation");
			setAttribute("type", "multiary");
			contextOpen("operand");
			moveHere(previousContext);
			contextClose();
		}
		contextOpen("operator");
		setAttribute("operator", orOp);
		if (verbosity >= 100)
			super.or_op(orOp);
		contextClose();
		contextOpen("operand");
	}

	public void equiv_op(Token equivOp) {
		// TODO Auto-generated method stub
		super.equiv_op(equivOp);
	}

	public void assignment_stmt(Token label, Token eos) {
		ArrayList<Element> nodes = contextNodes();
		if (nodes.size() < 2)
			cleanUpAfterError("there should be at least 2 nodes for 'assignment' but " + nodes.size() + " found");
		Element target = contextNode(-2);
		Element value = contextNode(-1);
		contextOpen("assignment");
		contextOpen("target");
		moveHere(target);
		contextClose();
		contextOpen("value");
		moveHere(value);
		contextClose();
		if (verbosity >= 100)
			super.assignment_stmt(label, eos);
		contextClose("assignment");
	}

	public void forall_header() {
		contextClose("header");
		if (verbosity >= 100)
			super.forall_header();
		contextOpen("body");
		contextOpen("statement");
	}

	public void forall_triplet_spec(Token id, boolean hasStride) {
		contextCloseAllInner("index-variable");
		setAttribute("name", id);
		super.forall_triplet_spec(id, hasStride);
		contextClose();
		contextOpen("index-variable");
	}

	public void forall_triplet_spec_list__begin() {
		if (contextTryFind("loop") == null) {
			contextRename("statement", "loop");
			setAttribute("type", "forall");
			setAttribute("subtype", "concurrent");
			contextOpen("header");
		}
		contextOpen("index-variables");
		if (verbosity >= 100)
			super.forall_triplet_spec_list__begin();
		contextOpen("index-variable");
	}

	public void forall_triplet_spec_list(int count) {
		contextClose("index-variable");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.forall_triplet_spec_list(count);
		contextClose("index-variables");
	}

	public void forall_assignment_stmt(boolean isPointerAssignment) {
		context = contextNode(-1); // temporarily reopen previously-closed context
		if (verbosity >= 100)
			super.forall_assignment_stmt(isPointerAssignment);
		contextClose(); // re-close previously closed context
	}

	public void forall_stmt__begin() {
		contextRename("statement", "loop");
		setAttribute("type", "forall");
		if (verbosity >= 100)
			super.forall_stmt__begin();
		contextOpen("header");
	}

	public void forall_stmt(Token label, Token forallKeyword) {
		contextCloseAllInner("loop");
		super.forall_stmt(label, forallKeyword);
		contextClose();
		contextOpen("statement"); // TODO: temporary workaround
	}

	public void block() {
		contextCloseAllInner("body");
		if (verbosity >= 100)
			super.block();
	}

	public void if_construct() {
		contextCloseAllInner("if");
		if (verbosity >= 100)
			super.if_construct();
		contextClose();
		contextOpen("statement");
	}

	public void if_then_stmt(Token label, Token id, Token ifKeyword, Token thenKeyword, Token eos) {
		contextRename("statement", "if");
		ArrayList<Element> nodes = contextNodes();
		contextOpen("header");
		moveHere(nodes);
		contextClose();
		if (verbosity >= 80)
			super.if_then_stmt(label, id, ifKeyword, thenKeyword, eos);
		contextOpen("body");
		contextOpen("statement");
	}

	public void else_if_stmt(Token label, Token elseKeyword, Token ifKeyword, Token thenKeyword, Token id, Token eos) {
		Element condition = contextNode(-1);
		contextClose("body");
		contextOpen("header");
		setAttribute("type", "else-if");
		moveHere(condition);
		contextClose();
		if (verbosity >= 80)
			super.else_if_stmt(label, elseKeyword, ifKeyword, thenKeyword, id, eos);
		contextOpen("body");
		setAttribute("type", "else-if");
		contextOpen("statement");
	}

	public void else_stmt(Token label, Token elseKeyword, Token id, Token eos) {
		contextClose("body");
		if (verbosity >= 80)
			super.else_stmt(label, elseKeyword, id, eos);
		contextOpen("body");
		setAttribute("type", "else");
		contextOpen("statement");
	}

	public void end_if_stmt(Token label, Token endKeyword, Token ifKeyword, Token id, Token eos) {
		contextCloseAllInner("if");
		if (verbosity >= 80)
			super.end_if_stmt(label, endKeyword, ifKeyword, id, eos);
	}

	public void if_stmt__begin() {
		contextRename("statement", "if");
		if (verbosity >= 100)
			super.if_stmt__begin();
		contextOpen("header"); // will be filled by if_stmt()
		contextClose();
		contextOpen("body");
		contextOpen("statement");
	}

	public void if_stmt(Token label, Token ifKeyword) {
		contextClose("body");
		Element ifHeader = contextNode(-2);
		Element ifBody = contextNode(-1);
		Element statementToBeFixed = contextNode(ifBody, 0);
		Element ifCondition = contextNode(statementToBeFixed, 0);
		if (!ifBody.getTagName().equals("body"))
			throw new IllegalArgumentException();
		moveTo(ifHeader, ifCondition);
		contextCloseAllInner("if");
		super.if_stmt(label, ifKeyword);
		contextClose();
		contextOpen("statement");
	}

	public void block_construct() {
		if (verbosity >= 100)
			super.block_construct();
	}

	public void case_construct() {
		contextCloseAllInner("select");
		if (verbosity >= 100)
			super.case_construct();
		contextClose();
		contextOpen("statement");
	}

	public void select_case_stmt(Token label, Token id, Token selectKeyword, Token caseKeyword, Token eos) {
		contextRename("statement", "select");
		ArrayList<Element> nodes = contextNodes();
		contextOpen("header");
		moveHere(nodes);
		contextClose();
		super.select_case_stmt(label, id, selectKeyword, caseKeyword, eos);
		contextOpen("body");
	}

	public void case_stmt(Token label, Token caseKeyword, Token id, Token eos) {
		super.case_stmt(label, caseKeyword, id, eos);
		contextOpen("body");
		contextOpen("statement");
	}

	public void end_select_stmt(Token label, Token endKeyword, Token selectKeyword, Token id, Token eos) {
		contextCloseAllInner("select");
		super.end_select_stmt(label, endKeyword, selectKeyword, id, eos);
	}

	public void case_selector(Token defaultToken) {
		if (!context.getTagName().equals("case") && contextTryFind("case") != null) {
			contextClose("case");
			contextOpen("case");
			setAttribute("type", "default");
			contextOpen("header");
			contextClose();
		}
		super.case_selector(defaultToken);
	}

	public void case_value_range() {
		contextClose("value-range");
		if (verbosity >= 100)
			super.case_value_range();
		contextOpen("value-range");
		contextOpen("value");
	}

	public void case_value_range_list__begin() {
		if (context.getTagName().equals("body") && ((Element) context.getParentNode()).getTagName().equals("case")) {
			contextClose("body");
			contextClose("case");
		}
		contextOpen("case");
		setAttribute("type", "specific");
		contextOpen("header");
		contextOpen("value-ranges");
		if (verbosity >= 100)
			super.case_value_range_list__begin();
		contextOpen("value-range");
		contextOpen("value");
	}

	public void case_value_range_list(int count) {
		contextCloseAllInner("value-ranges");
		if (verbosity >= 100)
			super.case_value_range_list(count);
		setAttribute("count", count);
		contextClose();
		contextClose("header");
	}

	public void case_value_range_suffix() {
		contextCloseAllInner("value-range");
		if (verbosity >= 100)
			super.case_value_range_suffix();
	}

	public void case_value() {
		contextClose("value");
		if (verbosity >= 100)
			super.case_value();
		contextOpen("value");
	}

	public void do_construct() {
		contextCloseAllInner("loop");
		if (verbosity >= 100)
			super.do_construct();
		contextClose();
		contextOpen("statement");
	}

	public void block_do_construct() {
		if (verbosity >= 100)
			super.block_do_construct();
	}

	public void do_stmt(Token label, Token id, Token doKeyword, Token digitString, Token eos, boolean hasLoopControl) {
		if (!context.getTagName().equals("header")) {
			if (!context.getTagName().equals("loop"))
				contextOpen("loop");
			contextOpen("header");
		}
		contextClose("header");
		if (digitString != null) {
			setAttribute("label", digitString);
		}
		super.do_stmt(label, id, doKeyword, digitString, eos, hasLoopControl);
		contextOpen("body");
		contextOpen("statement");
	}

	public void label_do_stmt(Token label, Token id, Token doKeyword, Token digitString, Token eos,
			boolean hasLoopControl) {
		contextClose("header");
		super.label_do_stmt(label, id, doKeyword, digitString, eos, hasLoopControl);
		contextOpen("body");
		contextOpen("statement");
	}

	public void loop_control(Token whileKeyword, int doConstructType, boolean hasOptExpr) {
		if (!context.getTagName().equals("statement"))
			contextClose("index-variable");
		else
			contextRename("statement", "loop");
		String loopType = "";
		switch (doConstructType) {
		case 1700:
			loopType = "do-concurrent";
			break;
		case 1701:
			loopType = "do";
			break;
		case 1702:
			Element node = contextNode(-1);
			contextOpen("header");
			moveHere(node);
			contextClose();
			loopType = "do-while";
			break;
		default:
			throw new IllegalArgumentException(Integer.toString(doConstructType));
		}
		super.loop_control(whileKeyword, doConstructType, hasOptExpr);
		setAttribute("type", loopType, "loop");
	}

	public void do_variable(Token id) {
		if (context.getTagName().equals("statement")) {
			contextRename("statement", "loop");
			setAttribute("type", "do");
		} else if (context.getTagName().equals("value")) {
			// nothing special needed
			// contextClose();
			// contextRename("array-constructor-values", "loop");
			// setAttribute("type", "array-constructor");
		} else if (context.getTagName().equals("outputs") || context.getTagName().equals("inputs")) {
			// TODO do this properly
			contextOpen("loop");
		} else
			cleanUpAfterError("unexpected context of 'do-variable': '" + context.getTagName() + "'");
		contextOpen("header");
		contextOpen("index-variable");
		setAttribute("name", id);
		super.do_variable(id);
	}

	public void end_do() {
		if (verbosity >= 100)
			super.end_do();
	}

	public void end_do_stmt(Token label, Token endKeyword, Token doKeyword, Token id, Token eos) {
		contextCloseAllInner("loop");
		if (verbosity >= 80)
			super.end_do_stmt(label, endKeyword, doKeyword, id, eos);
	}

	public void continue_stmt(Token label, Token continueKeyword, Token eos) {
		contextOpen("statement");
		super.continue_stmt(label, continueKeyword, eos);
	}

	public void stop_stmt(Token label, Token stopKeyword, Token eos, boolean hasStopCode) {
		if (hasStopCode) {
			Element value = contextNode(-1);
			contextOpen("stop");
			moveHere(value);
			Node stopCode = value.getAttributes().getNamedItem("digitString");
			setAttribute("code", stopCode.getNodeValue());
		} else {
			contextOpen("stop");
			setAttribute("code", "");
		}
		if (verbosity >= 60)
			super.stop_stmt(label, stopKeyword, eos, hasStopCode);
		contextClose();
	}

	public void open_stmt(Token label, Token openKeyword, Token eos) {
		Element args = contextNode(-1);
		contextOpen("open");
		moveHere(args);
		super.open_stmt(label, openKeyword, eos);
		contextClose();
	}

	public void connect_spec(Token id) {
		contextCloseAllInner("keyword-argument");
		setAttribute("argument-name", id);
		contextClose();
		if (verbosity >= 100)
			super.connect_spec(id);
		contextOpen("keyword-argument");
	}

	public void connect_spec_list__begin() {
		contextOpen("keyword-arguments");
		if (verbosity >= 100)
			super.connect_spec_list__begin();
		contextOpen("keyword-argument");
	}

	public void connect_spec_list(int count) {
		contextClose("keyword-argument");
		contextCloseAllInner("keyword-arguments");
		if (verbosity >= 100)
			super.connect_spec_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void close_stmt(Token label, Token closeKeyword, Token eos) {
		Element args = contextNode(-1);
		contextOpen("close");
		moveHere(args);
		super.close_stmt(label, closeKeyword, eos);
		contextClose();
	}

	public void close_spec(Token closeSpec) {
		contextCloseAllInner("keyword-argument");
		setAttribute("argument-name", closeSpec);
		contextClose();
		if (verbosity >= 100)
			super.close_spec(closeSpec);
		contextOpen("keyword-argument");
	}

	public void close_spec_list__begin() {
		contextOpen("keyword-arguments");
		if (verbosity >= 100)
			super.close_spec_list__begin();
		contextOpen("keyword-argument");
	}

	public void close_spec_list(int count) {
		contextClose("keyword-argument");
		contextCloseAllInner("keyword-arguments");
		if (verbosity >= 100)
			super.close_spec_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void read_stmt(Token label, Token readKeyword, Token eos, boolean hasInputItemList) {
		Element outerContext = context;
		contextOpen("read");
		if (hasInputItemList)
			moveHere(contextNode(outerContext, -3));
		moveHere(contextNode(outerContext, -2));
		super.read_stmt(label, readKeyword, eos, hasInputItemList);
		contextClose();
	}

	public void write_stmt(Token label, Token writeKeyword, Token eos, boolean hasOutputItemList) {
		Element args = contextNode(-1);
		Element outputs = null;
		if (hasOutputItemList) {
			outputs = args;
			args = contextNode(-2);
		}
		contextOpen("write");
		moveHere(args);
		if (hasOutputItemList)
			moveHere(outputs);
		super.write_stmt(label, writeKeyword, eos, hasOutputItemList);
		contextClose();
	}

	public void print_stmt(Token label, Token printKeyword, Token eos, boolean hasOutputItemList) {
		Element outputs = null;
		if (hasOutputItemList)
			outputs = contextNode(-1);
		contextOpen("print");
		if (hasOutputItemList)
			moveHere(outputs);
		super.print_stmt(label, printKeyword, eos, hasOutputItemList);
		contextClose();
	}

	public void io_control_spec(boolean hasExpression, Token keyword, boolean hasAsterisk) {
		if (hasExpression) {
			Element element = contextNode(-1);
			contextOpen("io-control");
			moveHere(element);
		} else
			contextOpen("io-control");
		setAttribute("argument-name", keyword == null ? "" : keyword);
		super.io_control_spec(hasExpression, keyword, hasAsterisk);
		contextClose("io-control");
	}

	public void io_control_spec_list__begin() {
		contextOpen("io-controls");
		if (verbosity >= 100)
			super.io_control_spec_list__begin();
	}

	public void io_control_spec_list(int count) {
		contextCloseAllInner("io-controls");
		if (verbosity >= 100)
			super.io_control_spec_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void input_item() {
		Element element = contextNode(-1);
		contextOpen("input");
		moveHere(element);
		if (verbosity >= 100)
			super.input_item();
		contextClose("input");
	}

	public void input_item_list__begin() {
		contextOpen("inputs");
		if (verbosity >= 100)
			super.input_item_list__begin();
	}

	public void input_item_list(int count) {
		contextCloseAllInner("inputs");
		if (verbosity >= 100)
			super.input_item_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void output_item() {
		Element element = contextNode(-1);
		contextOpen("output");
		moveHere(element);
		if (verbosity >= 100)
			super.output_item();
		contextClose();
	}

	public void output_item_list__begin() {
		contextOpen("outputs");
		if (verbosity >= 100)
			super.output_item_list__begin();
	}

	public void output_item_list(int count) {
		contextCloseAllInner("outputs");
		if (verbosity >= 100)
			super.output_item_list(count);
		setAttribute("count", count);
		contextClose();
	}

	public void io_implied_do() {
		contextCloseAllInner("loop");
		setAttribute("type", "implied-do");
		super.io_implied_do();
		Element loop = context;
		contextClose();
		ArrayList<Element> nodesBeforeLoop = contextNodes();
		nodesBeforeLoop.remove(loop); // remove loop
		Element loopHeader = contextNode(loop, 0);
		Element loopBody = doc.createElement("body");
		loop.insertBefore(loopBody, loopHeader);
		for (Element node : nodesBeforeLoop) {
			if (node.getTagName().equals("expression"))
				moveTo(loopBody, node);
		}
	}

	public void io_implied_do_object() {
		context = contextNode(-1);
		contextRename("expression");
		if (verbosity >= 100)
			super.io_implied_do_object();
		contextClose();
	}

	public void io_implied_do_control(boolean hasStride) {
		// TODO in the future, use hasStride to construct index-variable node from scratch
		contextClose("index-variable");
		contextClose("header");
		super.io_implied_do_control(hasStride);
	}

	public void format_item_list__begin() {
		// contextOpen("declaration");
		if (verbosity >= 100)
			super.format_item_list__begin();
	}

	public void main_program__begin() {
		contextOpen("program");
		if (verbosity >= 100)
			super.main_program__begin();
		contextOpen("header");
	}

	public void ext_function_subprogram(boolean hasPrefix) {
		context = contextNode(-1); // temporarily reopen previously-closed context
		if (verbosity >= 100)
			super.ext_function_subprogram(hasPrefix);
		contextClose(); // re-close previously closed context
	}

	public void main_program(boolean hasProgramStmt, boolean hasExecutionPart, boolean hasInternalSubprogramPart) {
		super.main_program(hasProgramStmt, hasExecutionPart, hasInternalSubprogramPart);
		contextClose("program");
	}

	public void program_stmt(Token label, Token programKeyword, Token id, Token eos) {
		contextClose("header");
		if (verbosity >= 20)
			super.program_stmt(label, programKeyword, id, eos);
		setAttribute("name", id);
		contextOpen("body");
		contextOpen("specification");
		contextOpen("declaration");
	}

	public void end_program_stmt(Token label, Token endKeyword, Token programKeyword, Token id, Token eos) {
		if (contextTryFind("program") == null) {
			// TODO: this workaround should not be needed
			ArrayList<Element> nodes = contextNodes();
			contextOpen("program");
			moveHere(nodes);
		}
		contextCloseAllInner("program");
		super.end_program_stmt(label, endKeyword, programKeyword, id, eos);
	}

	public void module() {
		contextCloseAllInner("module");
		if (verbosity >= 100)
			super.module();
		contextClose();
	}

	public void module_stmt__begin() {
		contextOpen("module");
		if (verbosity >= 100)
			super.module_stmt__begin();
		contextOpen("header");
	}

	public void module_stmt(Token label, Token moduleKeyword, Token id, Token eos) {
		contextClose("header");
		setAttribute("name", id);
		super.module_stmt(label, moduleKeyword, id, eos);
		contextOpen("body");
		contextOpen("specification");
		contextOpen("declaration");
	}

	public void end_module_stmt(Token label, Token endKeyword, Token moduleKeyword, Token id, Token eos) {
		if (!context.getTagName().equals("members")) {
			ArrayList<String> hierarchy = contextNameHierarchy();
			String[] expected = { "body", "module" };
			if (hierarchy.size() >= 2 && (Arrays.equals(hierarchy.subList(0, 2).toArray(), expected)
					|| (hierarchy.size() >= 3 && Arrays.equals(hierarchy.subList(1, 3).toArray(), expected)))) {
				contextClose("body");
				contextOpen("members");
			}
			/*
			else
				System.err.println("Context hierarchy for 'end module' statement: " + hierarchy);
			*/
		}
		contextClose("members");
		super.end_module_stmt(label, endKeyword, moduleKeyword, id, eos);
	}

	public void module_subprogram(boolean hasPrefix) {
		super.module_subprogram(hasPrefix);
	}

	public void use_stmt(Token label, Token useKeyword, Token id, Token onlyKeyword, Token eos, boolean hasModuleNature,
			boolean hasRenameList, boolean hasOnly) {
		if (!context.getTagName().equals("use")) {
			contextOpen("use");
		}
		super.use_stmt(label, useKeyword, id, onlyKeyword, eos, hasModuleNature, hasRenameList, hasOnly);
		contextClose("use");
	}

	public void rename_list__begin() {
		contextOpen("use");
		contextOpen("rename");
		if (verbosity >= 100)
			super.rename_list__begin();
	}

	public void rename_list(int count) {
		super.rename_list(count);
		contextClose("rename");
	}

	public void only_list__begin() {
		contextOpen("use");
		contextOpen("only");
		if (verbosity >= 100)
			super.only_list__begin();
	}

	public void only_list(int count) {
		super.only_list(count);
		contextClose("only");
	}

	public void block_data() {
		if (verbosity >= 100)
			super.block_data();
		contextClose("block-data");
	}

	public void block_data_stmt__begin() {
		contextOpen("block-data");
		if (verbosity >= 100)
			super.block_data_stmt__begin();
		contextOpen("specification");
		contextOpen("declaration");
	}

	public void interface_block() {
		// TODO Auto-generated method stub
		super.interface_block();
	}

	public void interface_specification() {
		// TODO Auto-generated method stub
		super.interface_specification();
	}

	public void interface_stmt__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		contextOpen("interface");
		if (verbosity >= 100)
			super.interface_stmt__begin();
		contextOpen("header");
	}

	public void interface_stmt(Token label, Token abstractToken, Token keyword, Token eos, boolean hasGenericSpec) {
		Element previous_context = context;
		contextClose("header");
		if (context.getTagName() != "interface") {
			context = previous_context;
			contextOpen("declaration");
			contextOpen("interface");
			contextOpen("header");
			contextClose();
		}
		super.interface_stmt(label, abstractToken, keyword, eos, hasGenericSpec);
		if (abstractToken != null) // && abstractToken.getText().toLowerCase() == "abstract")
			setAttribute("type", abstractToken);
		contextOpen("body");
		contextOpen("specification");
		contextOpen("declaration");
	}

	public void end_interface_stmt(Token label, Token kw1, Token kw2, Token eos, boolean hasGenericSpec) {
		contextCloseAllInner("interface");
		super.end_interface_stmt(label, kw1, kw2, eos, hasGenericSpec);
		contextClose();
	}

	public void interface_body(boolean hasPrefix) {
		// TODO Auto-generated method stub
		super.interface_body(hasPrefix);
	}

	public void import_stmt(Token label, Token importKeyword, Token eos, boolean hasGenericNameList) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "import");
		super.import_stmt(label, importKeyword, eos, hasGenericNameList);
		contextClose("declaration");
	}

	public void external_stmt(Token label, Token externalKeyword, Token eos) {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		if (verbosity >= 80)
			super.external_stmt(label, externalKeyword, eos);
		setAttribute("type", "external");
	}

	public void procedure_declaration_stmt(Token label, Token procedureKeyword, Token eos, boolean hasProcInterface,
			int count) {
		// TODO Auto-generated method stub
		super.procedure_declaration_stmt(label, procedureKeyword, eos, hasProcInterface, count);
	}

	public void proc_decl(Token id, boolean hasNullInit) {
		contextOpen("procedure");
		setAttribute("name", id);
		if (verbosity >= 80)
			super.proc_decl(id, hasNullInit);
		contextClose();
	}

	public void proc_decl_list__begin() {
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "procedures");
		contextOpen("procedures");
		if (verbosity >= 100)
			super.proc_decl_list__begin();
	}

	public void proc_decl_list(int count) {
		contextCloseAllInner("procedures");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.proc_decl_list(count);
		contextClose();
	}

	public void intrinsic_stmt(Token label, Token intrinsicKeyword, Token eos) {
		Element condition = contextNode(-1);
		if (!context.getTagName().equals("declaration"))
			contextOpen("declaration");
		setAttribute("type", "intrinsic");
		moveHere(condition);
		super.intrinsic_stmt(label, intrinsicKeyword, eos);
	}

	public void call_stmt(Token label, Token callKeyword, Token eos, boolean hasActualArgSpecList) {
		Element name = contextNode(-1);
		Element arguments = null;
		if (name.getTagName() == "arguments") {
			arguments = name;
			name = contextNode(-2);
		} else if (name.getTagName() != "name")
			cleanUpAfterError("tag name is not 'name' but '" + name.getTagName() + "'");
		contextOpen("call");
		moveHere(name);
		if (arguments != null)
			moveHere(arguments);
		super.call_stmt(label, callKeyword, eos, hasActualArgSpecList);
		contextClose();
	}

	public void procedure_designator() {
		if (verbosity >= 100)
			super.procedure_designator();
		setAttribute("type", "procedure");
		contextClose("name");
	}

	public void actual_arg_spec(Token keyword) {
		boolean inArgumentContext = contextTryFind("argument") != null;
		if (!inArgumentContext)
			contextOpen("argument");
		setAttribute("name", keyword);
		if (verbosity >= 100)
			super.actual_arg_spec(keyword);
		if (inArgumentContext)
			contextClose("argument");
	}

	public void actual_arg_spec_list__begin() {
		contextOpen("arguments");
		if (verbosity >= 100)
			super.actual_arg_spec_list__begin();
		contextOpen("argument");
	}

	public void actual_arg_spec_list(int count) {
		contextClose("argument");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.actual_arg_spec_list(count);
		contextClose("arguments");
	}

	public void actual_arg(boolean hasExpr, Token label) {
		boolean inArgumentContext = contextTryFind("argument") != null;
		if (!inArgumentContext) {
			if (hasExpr) {
				Element element = contextNode(-1);
				contextOpen("argument");
				moveHere(element);
			} else
				contextOpen("argument");
		}
		if (verbosity >= 60)
			super.actual_arg(hasExpr, label);
		if (inArgumentContext)
			contextClose("argument");
	}

	public void function_subprogram(boolean hasExePart, boolean hasIntSubProg) {
		super.function_subprogram(hasExePart, hasIntSubProg);
		if (context.getTagName().equals("function"))
			contextClose("function");
	}

	public void function_stmt__begin() {
		contextOpen("function");
		contextOpen("header");
		if (verbosity >= 100)
			super.function_stmt__begin();
	}

	public void function_stmt(Token label, Token keyword, Token name, Token eos, boolean hasGenericNameList,
			boolean hasSuffix) {
		contextClose("header");
		super.function_stmt(label, keyword, name, eos, hasGenericNameList, hasSuffix);
		setAttribute("name", name);
		contextOpen("body");
		contextOpen("specification");
		contextOpen("declaration");
	}

	public void prefix_spec(boolean isDecTypeSpec) {
		super.prefix_spec(isDecTypeSpec);
		if (isDecTypeSpec)
			contextClose("declaration");
	}

	public void end_function_stmt(Token label, Token keyword1, Token keyword2, Token name, Token eos) {
		contextCloseAllInner("function");
		super.end_function_stmt(label, keyword1, keyword2, name, eos);
	}

	public void subroutine_stmt__begin() {
		contextOpen("subroutine");
		contextOpen("header");
		if (verbosity >= 100)
			super.subroutine_stmt__begin();
	}

	public void subroutine_stmt(Token label, Token keyword, Token name, Token eos, boolean hasPrefix,
			boolean hasDummyArgList, boolean hasBindingSpec, boolean hasArgSpecifier) {
		super.subroutine_stmt(label, keyword, name, eos, hasPrefix, hasDummyArgList, hasBindingSpec, hasArgSpecifier);
		contextClose("header");
		setAttribute("name", name);
		contextOpen("body");
		contextOpen("specification");
		contextOpen("declaration");
	}

	public void dummy_arg(Token dummy) {
		contextOpen("argument");
		setAttribute("name", dummy);
		if (verbosity >= 100)
			super.dummy_arg(dummy);
		contextClose();
	}

	public void dummy_arg_list__begin() {
		contextOpen("arguments");
		if (verbosity >= 100)
			super.dummy_arg_list__begin();
	}

	public void dummy_arg_list(int count) {
		contextCloseAllInner("arguments");
		setAttribute("count", count);
		if (verbosity >= 100)
			super.dummy_arg_list(count);
		contextClose();
	}

	public void end_subroutine_stmt(Token label, Token keyword1, Token keyword2, Token name, Token eos) {
		contextCloseAllInner("subroutine");
		super.end_subroutine_stmt(label, keyword1, keyword2, name, eos);
		contextClose();
	}

	public void return_stmt(Token label, Token keyword, Token eos, boolean hasScalarIntExpr) {
		if (hasScalarIntExpr) {
			Element element = contextNode(-1);
			contextOpen("return");
			contextOpen("value");
			moveHere(element);
			contextClose();
		} else
			contextOpen("return");
		setAttribute("hasValue", hasScalarIntExpr);
		super.return_stmt(label, keyword, eos, hasScalarIntExpr);
		contextClose();
	}

	public void contains_stmt(Token label, Token keyword, Token eos) {
		ArrayList<String> hierarchy = contextNameHierarchy();
		String[] expected = { "statement", "body", "module" };
		boolean inModuleBody = hierarchy.size() >= 3 && Arrays.equals(hierarchy.subList(0, 3).toArray(), expected);
		if (inModuleBody)
			contextClose("body");
		/*
		else
			System.err.println("Context hierarchy for 'contains' statement: " + hierarchy);
		*/
		super.contains_stmt(label, keyword, eos);
		if (inModuleBody)
			contextOpen("members");
	}

	public void separate_module_subprogram(boolean hasExecutionPart, boolean hasInternalSubprogramPart) {
		super.separate_module_subprogram(hasExecutionPart, hasInternalSubprogramPart);
		contextClose("subroutine");
	}

	public void separate_module_subprogram__begin() {
		contextOpen("subroutine");
		super.separate_module_subprogram__begin();
		contextOpen("header");
	}

	public void mp_subprogram_stmt(Token label, Token moduleKeyword, Token procedureKeyword, Token name, Token eos) {
		contextClose("header");
		setAttribute("name", name);
		super.mp_subprogram_stmt(label, moduleKeyword, procedureKeyword, name, eos);
		contextOpen("body");
	}

	public void end_mp_subprogram_stmt(Token label, Token keyword1, Token keyword2, Token name, Token eos) {
		contextCloseAllInner("subroutine");
		super.end_mp_subprogram_stmt(label, keyword1, keyword2, name, eos);
	}

	public void start_of_file(String filename, String path) {
		if (contextTryFind("file") != null) {
			if (context.getTagName().equals("declaration"))
				contextClose("declaration");
			contextOpen("declaration");
			setAttribute("type", "include");
		}
		contextOpen("file");
		if (verbosity >= 100)
			super.start_of_file(filename, path);
		if (path.equals("ERROR_FILE_NOT_FOUND"))
			setAttribute("path", filename);
		else
			setAttribute("path", path);
	}

	public void end_of_file(String filename, String path) {
		contextCloseAllInner("file");
		if (verbosity >= 100)
			super.end_of_file(filename, path);
		contextClose();
	}

	public void cleanUpAfterError(String comment, Exception error) {
		if (comment != null)
			System.err.println(comment);
		new RuntimeException("Aborting construction of the AST.", error).printStackTrace();
		cleanUp();
		System.exit(1);
	}

	public void cleanUpAfterError(String comment) {
		cleanUpAfterError(comment, null);
	}

	public void cleanUpAfterError(Exception error) {
		cleanUpAfterError(null, error);
	}

	public void cleanUpAfterError() {
		cleanUpAfterError(null, null);
	}

	public void cleanUp() {
		while (context != root)
			contextClose(context);
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			DOMSource source = new DOMSource(doc);
			StreamResult result;
			if (cmd.hasOption("output"))
				result = new StreamResult(new File(cmd.getOptionValue("output")));
			else
				result = new StreamResult(System.out);
			transformer.transform(source, result);
		} catch (Exception error) {
			error.printStackTrace();
			System.exit(1);
		}
	}

	public void next_token(Token tk) {
		System.err.println("next_token");
		System.err.println(tk);
	}

}
