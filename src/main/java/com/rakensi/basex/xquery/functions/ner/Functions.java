package com.rakensi.basex.xquery.functions.ner;

import java.net.URI;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.Map;

import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.expr.Expr;
import org.basex.query.func.StandardFunc;
import org.basex.query.util.list.AnnList;
import org.basex.query.value.Value;
import org.basex.query.value.item.FuncItem;
import org.basex.query.value.item.QNm;
import org.basex.query.value.item.Str;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.FElem;
import org.basex.query.value.node.FNode;
import org.basex.query.value.type.FuncType;
import org.basex.query.value.type.SeqType;
import org.basex.query.var.Var;
import org.basex.query.var.VarScope;
import org.basex.util.hash.TokenMap;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.greenmercury.smax.SmaxException;
import org.greenmercury.smax.convert.DomElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.rakensi.xml.ner.Logger;
import com.rakensi.xml.ner.NamedEntityRecognition;

public class Functions extends QueryModule
{

  /**
   * A simple logger that can be used in the named entity recognition function.
   * @param qc the query context
   * @return a very simple logger
   */
  private static Logger logger(final QueryContext qc) {
    return new Logger() {
      @Override
      public void info(String message)
      {
        qc.context.log.write("INFO", message, null, qc.context);
      }
      @Override
      public void warning(String message)
      {
        qc.context.log.write("WARNING", message, null, qc.context);
      }
      @Override
      public void error(String message)
      {
        qc.context.log.write("ERROR", message, null, qc.context);
      }};
  }

  /**
   * The named entity recognition function:
   * named-entity-recognition($grammar as item(), $options as map(*)?)  as  function(item()) as node()*
   */
  @Requires(Permission.NONE)
  @Deterministic
  @ContextDependent
  public FuncItem namedEntityRecognition(Object grammar, Map<String, String> options) throws QueryException {
    Logger logger = logger(queryContext);
    final Var[] params = { new VarScope().addNew(new QNm("input"), SeqType.ITEM_O, queryContext, null) }; // Types of the arguments of the generated function.
    NamedEntityRecognitionFunction nerf = new NamedEntityRecognitionFunction(logger, grammar, options);
    final FuncType ft = FuncType.get(nerf.seqType(), SeqType.ITEM_O); // Type of the generated function.
    return new FuncItem(null, nerf, params, AnnList.EMPTY, ft, params.length, null);
  }

  /**
   * The generated NER matcher function.
   */
  private static final class NamedEntityRecognitionFunction extends StandardFunc {

    private final Logger logger;
    private final NamedEntityRecognition ner;

    protected NamedEntityRecognitionFunction(Logger logger, Object grammar, Map<String, String> options)
    throws QueryException
    {
      this.logger = logger;
      try {
        if (grammar instanceof URL) {
          this.ner = new NamedEntityRecognition((URL)grammar, options, logger);
        } else if (grammar instanceof URI) {
          this.ner = new NamedEntityRecognition(((URI)grammar).toURL(), options, logger);
        } else if (grammar instanceof ANode) {
          this.ner = new NamedEntityRecognition(new String(((ANode)grammar).string()), options, logger);
        } else if (grammar instanceof String) {
          this.ner = new NamedEntityRecognition((String)grammar, options, logger);
        } else {
          throw new InvalidParameterException("The first parameter ($grammar) of named-entity-recognition can not be a "+grammar.getClass().getName());
        }
      } catch (Exception e) {
        throw new QueryException(e);
      }
    }

    /**
     * Evaluate the generated NER function.
     */
    @Override
    public Value value(final QueryContext qc)
    throws QueryException
    {
      Expr inputExpr = arg(0);
      // Create a SMAX document with a <wrapper> root element around the input.
      SmaxDocument smaxDocument = null;
      if (inputExpr.seqType().instanceOf(SeqType.STRING_O)) {
        // Wrap the string in an element.
        final String inputString = ((Str)inputExpr.value(qc)).toJava();
        final SmaxElement wrapper = new SmaxElement("wrapper").setStartPos(0).setEndPos(inputString.length());
        smaxDocument = new SmaxDocument(wrapper, inputString);
      } else if (inputExpr.seqType().instanceOf(SeqType.NODE_O)) {
        Node inputNode = ((ANode)inputExpr).toJava();
        Element inputElement = wrap(inputNode);
        try{
          smaxDocument = DomElement.toSmax(inputElement);
        } catch (SmaxException e) {
          throw new QueryException(e);
        }
      } else {
        throw new QueryException("The generated NER function accepts a string or node, but not a "+inputExpr.seqType().typeString());
      }
      // Do Named Entity Recognition on the SMAX document.
      this.ner.scan(smaxDocument);
      // Convert the SMAX document to something that BaseX can use.
      try {
        Element outputElement = DomElement.documentFromSmax(smaxDocument).getDocumentElement();
        FNode resultWrapperElement = FElem.build(outputElement, new TokenMap()).finish();
        // Remove the wrapper element and return its contents.
        Value result = resultWrapperElement.childIter().value(qc, null);
        return result;
      } catch (Exception e) {
        throw new QueryException(e);
      }
    }

    /**
     * The org.basex.api.dom.BXNode does not implement appendChild().
     * Therefore, we have to make our own wrapper element, which needs to work for org.greenmercury.smax.convert.DomElement.toSmax(Element).
     * @param node A node that must be wrapped in a "wrapper" element.
     * @return The wrapper element.
     */
    private Element wrap(Node node)
    {
      Element wrapper = new VerySimpleElementImpl("wrapper");
      wrapper.appendChild(node);
      return wrapper;
    }

  }

}
