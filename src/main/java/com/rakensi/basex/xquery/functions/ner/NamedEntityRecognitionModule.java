package com.rakensi.basex.xquery.functions.ner;

import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.basex.query.CompileContext;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.QueryString;
import org.basex.query.expr.Arr;
import org.basex.query.expr.Expr;
import org.basex.query.func.java.JavaCall;
import org.basex.query.util.list.AnnList;
import org.basex.query.value.Value;
import org.basex.query.value.item.FuncItem;
import org.basex.query.value.item.QNm;
import org.basex.query.value.item.Str;
import org.basex.query.value.node.ANode;
import org.basex.query.value.type.FuncType;
import org.basex.query.value.type.SeqType;
import org.basex.query.var.Var;
import org.basex.query.var.VarRef;
import org.basex.query.var.VarScope;
import org.basex.util.hash.IntObjMap;
import org.basex.util.log.Log;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.Dom;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.rakensi.xml.ner.Logger;
import com.rakensi.xml.ner.NamedEntityRecognition;

public class NamedEntityRecognitionModule extends QueryModule
{

  /**
   * A simple logger that can be used in the named entity recognition function.
   * @param qc the query context
   * @return a very simple logger
   */
  private static Logger logger(final QueryContext qc) {
    Log basexLog = qc.context.log;
    return new Logger() {
      @Override
      public void info(String message)
      {
        basexLog.write("INFO", message, null, qc.context);
      }
      @Override
      public void warning(String message)
      {
        basexLog.write("WARNING", message, null, qc.context);
      }
      @Override
      public void error(String message)
      {
        basexLog.write("ERROR", message, null, qc.context);
      }};
  }

  /**
   * Make a NamedEntityRecognition instance.
   * @param grammar a grammar, as a URL, URI, Element or String.
   * @param options
   * @param logger a very simple logger.
   * @return a NamedEntityRecognition instance.
   * @throws QueryException
   */
  private static NamedEntityRecognition ner(Object grammar, Map<String, String> options, Logger logger) throws QueryException {
    try {
      if (grammar instanceof URL) {
        return new NamedEntityRecognition((URL)grammar, options, logger);
      } else if (grammar instanceof URI) {
        return new NamedEntityRecognition(((URI)grammar).toURL(), options, logger);
      } else if (grammar instanceof Element) {
        return new NamedEntityRecognition((Element)grammar, options, logger);
      } else if (grammar instanceof String) {
        return new NamedEntityRecognition((String)grammar, options, logger);
      } else {
        throw new IllegalArgumentException("The first parameter ($grammar) of named-entity-recognition can not be a "+grammar.getClass().getName());
      }
    } catch (Exception e) {
      throw new QueryException(e);
    }
  }

  /**
   * The named entity recognition function:
   * named-entity-recognition($grammar as item(), $options as map(*)?)  as  function(item()) as node()*
   */
  @Requires(Permission.NONE)
  @Deterministic
  @ContextDependent
  public FuncItem namedEntityRecognition(Object grammar, Map<String, String> options) throws QueryException {
    // Names and types of the arguments of the generated function.
    final Var[] generatedFunctionParameters = { new VarScope().addNew(new QNm("input"), SeqType.ITEM_O, queryContext, null) };
    final Expr[] generatedFunctionParameterExprs = { new VarRef(null, generatedFunctionParameters[0]) };
    // Result type of the generated function.
    final SeqType generatedFunctionResultType = SeqType.NODE_ZM;
    // Type of the generated function.
    final FuncType generatedFunctionType = FuncType.get(generatedFunctionResultType, generatedFunctionParameters[0].declType);
    // The generated function.
    NamedEntityRecognitionFunction nerf = new NamedEntityRecognitionFunction(grammar, options, generatedFunctionResultType, generatedFunctionParameterExprs, queryContext);
    // Return a function item.
    return new FuncItem(null, nerf, generatedFunctionParameters, AnnList.EMPTY, generatedFunctionType, generatedFunctionParameters.length, null);
  }

  /**
   * The generated NER matcher function.
   */
  private static final class NamedEntityRecognitionFunction extends Arr {

    private final NamedEntityRecognition ner;
    private final Logger logger;

    /**
     * Make a NamedEntityRecognitionFunction for a grammar and options
     * @param grammar
     * @param options
     * @param funcType
     * @param queryContext
     * @throws QueryException
     */
    protected NamedEntityRecognitionFunction(Object grammar, Map<String, String> options,
        SeqType generatedFunctionResultType, Expr[] generatedFunctionParameterExprs, QueryContext queryContext)
    throws QueryException
    {
      super(null, generatedFunctionResultType, generatedFunctionParameterExprs);
      this.logger = logger(queryContext);
      this.ner = ner(grammar, options, logger);
    }

    /**
     * Make a NamedEntityRecognitionFunction using properties of an existing NamedEntityRecognitionFunction.
     * @param ner
     * @param funcType
     * @param queryContext
     */
    private NamedEntityRecognitionFunction(NamedEntityRecognition ner, Logger logger,
        SeqType generatedFunctionResultType, Expr[] generatedFunctionParameterExprs)
    {
      super(null, generatedFunctionResultType, generatedFunctionParameterExprs);
      this.logger = logger;
      this.ner = ner;
    }

    /**
     * Evaluate the generated NER function.
     */
    @Override
    public Value value(final QueryContext qc)
    throws QueryException
    {
      Value inputValue = arg(0).value(qc);
      boolean inputIsString = inputValue.seqType().instanceOf(SeqType.STRING_O);
      boolean inputIsElement = inputValue.seqType().instanceOf(SeqType.ELEMENT_O);
      // Create a SMAX document from the input.
      SmaxDocument smaxDocument = null;
      if (inputIsString) {
        // Create a SMAX document with a <wrapper> root element around the input string.
        final String inputString = ((Str)inputValue).toJava();
        final SmaxElement wrapper = new SmaxElement("wrapper").setStartPos(0).setEndPos(inputString.length());
        smaxDocument = new SmaxDocument(wrapper, inputString);
      } else if (inputIsElement) {
        // Create a SMAX document from this element.
        try {
          smaxDocument = Dom.toSmax((Element)inputValue.toJava());
        } catch (SmaxException e) {
          throw new QueryException(e);
        }
      } else if (inputValue.seqType().instanceOf(SeqType.DOCUMENT_NODE_O)) {
        // Create a SMAX document from this document.
        try {
          smaxDocument = Dom.toSmax((Document)inputValue.toJava());
        } catch (SmaxException e) {
          throw new QueryException(e);
        }
      } else {
        throw new QueryException("The generated function accepts a string or document-node or element, but not a "+inputValue.seqType().typeString());
      }

      // Do Named Entity Recognition on the SMAX document.
      this.ner.scan(smaxDocument);

      // Convert the SMAX document to something that BaseX can use.
      Document outputDocument;
      try {
        outputDocument = Dom.documentFromSmax(smaxDocument);
      } catch (Exception e) {
        throw new QueryException(e);
      }
      ANode bxOutputDocument = (ANode)JavaCall.toValue(outputDocument, qc, null);
      if (inputIsString) {
        // Get the wrapper element and return its children.
        ANode wrapper = bxOutputDocument.childIter().next();
        return wrapper.childIter().value(qc, null);
      } else if (inputIsElement) {
        // Return the root element of the output document.
        return bxOutputDocument.childIter().next();
      } else {
        // Return the output document.
        return bxOutputDocument;
      }
    }

    @Override
    public Expr copy(CompileContext cc, IntObjMap<Var> vm)
    {
      Expr[] functionParameterExprs = copyAll(cc, vm, this.args());
      return copyType(new NamedEntityRecognitionFunction(this.ner, this.logger, this.seqType(), functionParameterExprs));
    }

    @Override
    public void toString(QueryString qs)
    {
      qs.token("generated-named-entity-recognition-function").params(exprs);
    }

  }

}
