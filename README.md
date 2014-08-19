UnitTagger
==========

This is a java software for extracting unit strings from plain text and from table headers.


Installation instructions
------------------------

Resolve dependencies as per ivy.xml
Download WordNet from http://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz
Download the JAW java library http://lyle.smu.edu/~tspell/jaws/jaws-bin.jar
Download weka-wrapper of https://github.com/bwaldvogel/liblinear-weka and compile as LibLinear.jar 
Edit configs/config.xml to point to the WordNet/dict directory.

If stored concept classifier throws java class serialization error,
run "java parser.ConceptClassifier train" to retrain the classifier from
the available training data.
