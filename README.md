# basex-ner-xar

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

Enter the following in a new XQuery document.

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

Press the 'Eval' button. The output should look like:

```
<r>RIC for <ric symbol="♵">vinyl</ric> and <ric symbol="♳">polyethylene</ric></r>
```

## See also

For more documentation, please have a look at the [XML-NER project](https://github.com/nverwer/XML-NER).
