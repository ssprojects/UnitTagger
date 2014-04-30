UnitTagger
==========

This is a java software for extracting unit strings from plain text and from table headers.


Installation instructions
------------------------

Resolve dependencies as per ivy.xml
Download WordNet from ..
Download the JAW java library
Edit configs/config.xml to point to the WordNet/dict directory.

If stored concept classifier throws java class serialization error, run java parser.ConceptClassifier train to retrain the classifier from the available training data.
