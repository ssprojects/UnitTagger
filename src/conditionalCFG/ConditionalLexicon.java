package conditionalCFG;

import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.UnaryRule;

public interface ConditionalLexicon extends Lexicon {
	public float score(BinaryRule rule, int start, int end, int split);
	public float score(UnaryRule rule, int start, int end);
}
