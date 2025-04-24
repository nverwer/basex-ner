# basex-ner

Named Entity Recognition for BaseX, using [XML-NER](https://github.com/nverwer/XML-NER).

This is not a XAR archive. I tried to make a XAR for BaseX, but it did not work.
Additionally, the [BaseX documentation](https://docs.basex.org/main/Repository#performance) says that performance will suffer,
so I decided to make a JAR instead.
Maybe one day I will try to make a XAR that actually works and performs well.

## Building from source

It is assumed that you have already installed BaseX and Maven.

Download or clone or fork the git repository, which has all the source files.
In the directory where you have just downloaded the project, do `maven install`. 
This will create a .jar file in the `target` directory.

## Installing

Copy the .jar file from the `target` directory into the `lib/custom` directory of your BaseX installation.
Start, or restart BaseX.

## Using

The Java function `FuncItem namedEntityRecognition(Object grammar, Map<String, String> options)` is available in XQuery as 

```xquery
ner:named-entity-recognition($grammar as (xs:string | xs:anyURI), $options as map(xs:string, xs:string))
  as function((xs:string | element() | document-node())) as node()*
```

where the `ner` namespace is `com.rakensi.basex.xquery.functions.ner.NamedEntityRecognitionModule`.
If the input to the returned `function((xs:string | element() | document-node())) as node()*` is a string,
the output is a sequence of text nodes and elements.
If the input is an element or a document node, the output has the same type.

As an example, consider the following XQuery document.

```
import module namespace ner='com.rakensi.basex.xquery.functions.ner.NamedEntityRecognitionModule';
let $grammar :=
  <grammar>
    <entity id="♳"><name>PET</name><name>polyethylene</name><name>terephthalate</name></entity>
    <entity id="♴"><name>HDPE</name><name>high-density polyethylene</name><name>polyethylene high-density</name></entity>
    <entity id="♵"><name>PVC</name><name>polyvinyl chloride</name><name>polyvinylchloride</name><name>vinyl</name><name>polyvinyl</name></entity>
  </grammar>
let $input := <r>RIC for vinyl and polyethylene</r>
let $ner-parse := ner:named-entity-recognition($grammar, map{'match-element-name': 'ric', 'match-attribute': 'symbol'})
return $ner-parse($input)
```

When this XQuery is run, the output should look like:

```
<r>RIC for <ric symbol="♵">vinyl</ric> and <ric symbol="♳">polyethylene</ric></r>
```

### Options

A map with options. The following options are recognized:

* `word-chars` Characters that are significant for matching an entity name. Default is `""`.
    Letters and digits are always significant, but characters like '.' and '-' are not.
    A sequence of non-significant characters and/or whitespace in a text will be treated as a single space during matching.
    This means that an entity name like "e.g." can only be recognized when '.' is in word-chars.
    Whitespace is ignored at the start and end of an entity name, and replaced by a single significant space in the middle.
* `no-word-before` Characters that may not immediately follow a word (next to letters and digits).
    They cannot follow the end of a match. Default is "".
* `no-word-after` Characters that may not immediately precede a word (next to letters and digits).
    Matches can only start on a letter or digit, and not after noWordAfter characters. Default is "".
* `case-insensitive-min-length` The minimum entity-length for case-insensitive matching.
    Text fragments larger than this will be scanned case-insensitively.
    This prevents short words to be recognized as abbreviations.
    Set to -1 to always match case-sensitive. Set to 0 to always match case-insensitive.
    Default is -1.
* `fuzzy-min-length` The minimum entity-length for fuzzy matching.
    Text fragments larger than this may contain characters that are not significant for matching.
    This prevents short words with noise to be recognized as abbreviations.
    Set to -1 to match exact. Set to 0 to match fuzzy.
    Default is -1.
* `balancing` The SMAX balancing strategy that is used when an element for a recognized entity is inserted.
    Default is "OUTER".
* `match-within-element` If set to the local name (without namespace prefix) of an element, only text within elements with this name will be matched.
* `match-within-namespace` If 'parse-within-element' is set, this may be set to the namespace URI of these elements.
* `match-element-name` The name of the element that is inserted around matched text fragments.
    Default is 'fn:match'.
* `match-element-namespace-uri` The namespace URI of the match element.
    This option must be present if the match-element-name contains a namespace prefix other than 'fn:'.
    If the namespace prefix in 'match-element-name' is 'fn:', the default is 'http://www.w3.org/2005/xpath-functions'.
* `match-attribute` The name of the attribute on the match element that will hold the id of the matching entity.
    Default is 'id'.

Setting case-insensitive-min-length to 4 prevents the scanner from recognizing "THE" in "Do the right thing".

Setting fuzzy-min-length to 4 prevents the scanner from recognizing "C F" in "C.F. Gauss was a German mathematician".

With these settings, "R S V P" would be recognized in "Put an r.s.v.p. at the end", provided that '.' is not in word-chars.

Setting case-insensitive-min-length and fuzzy-min-length to 3 or less will recognize "THE" and "C F" in "c.f. the r.s.v.p.".

All sequences of whitespace characters will be treated like a single space, both in the grammar input and the text that is scanned for named entities.

## See also

For more documentation, please have a look at the [XML-NER project](https://github.com/nverwer/XML-NER).
