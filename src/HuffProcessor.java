import java.lang.reflect.Array;
import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	private int[] readForCounts(BitInputStream inn) {
		int[] arr = new int[ALPH_SIZE + 1];
		while(true) {
			int read = inn.readBits(BITS_PER_WORD);
			if(read == -1) {
				break;
			}
			else {
				arr[BITS_PER_WORD]++;
			}
			arr[PSEUDO_EOF] = 1;
		}
		return arr;
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i = 0; i < counts.length; i++) {
			if(counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null));
			}
		}
		
		while(pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(left.myValue + right.myValue, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root, "", encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode r, String s, String[] e) {
		if(r == null) return;
		if(r.myLeft == null && r.myRight == null) {
			e[r.myValue] = s;
			return;
		}
		else {
			codingHelper(r.myLeft, s + "0", e);
			codingHelper(r.myRight, s + "1", e);
		}
	}
	
	private void writeHeader(HuffNode current, BitOutputStream o) {
		if(current == null) return;
			if(current.myLeft != null || current.myRight != null) {
				o.writeBits(1, 0); 
				writeHeader(current.myLeft, o);
				writeHeader(current.myRight, o);
			}
			else if(current.myLeft == null && current.myRight == null) {
				o.writeBits(1, 1);
				o.writeBits(BITS_PER_WORD + 1, current.myValue);
			}
	}
	
	private void writeCompressedBits(String[] c, BitInputStream in, BitOutputStream o) {
		while(true) {
			int bits = in.readBits(BITS_PER_WORD);
			if(bits == -1) {
				o.writeBits(c[PSEUDO_EOF].length(), Integer.parseInt(c[PSEUDO_EOF], 2));
			}
			else {
				String code = c[BITS_PER_WORD];
				if(code == null || o == null) {
					break;
				}
				o.writeBits(c.length, Integer.parseInt(code, 2));
			}
		}
	}
	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) {
			throw new HuffException("illegal  header starts with " + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream here) {
		int read = here.readBits(1);
		if(read == -1) {
			throw new HuffException("read -1");
		}
		
		if (read == 0) {
			HuffNode left = readTreeHeader(here);
			HuffNode right = readTreeHeader(here);
			return new HuffNode(0, 0, left, right);
		}
		
		else {
			int value = here.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null );
		}
	}
	
	private void readCompressedBits(HuffNode roott, BitInputStream inn, BitOutputStream outt) {
		HuffNode current = roott; 
		while(true) {
			int bits = inn.readBits(1);
			if(bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				if(bits == 0) current = current.myLeft;

				else current = current.myRight;
				
				if(current.myRight == null && current.myLeft == null) {
					if(current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						outt.writeBits(BITS_PER_WORD, current.myValue);
						current = roott;
					}
				}		
			}
		}
	}
}