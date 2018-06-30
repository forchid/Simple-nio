package io.simple.util;

/**
 * A math utils.
 * 
 * @author little-pan
 * @since 2018-06-30
 *
 */
public final class MathUtil {
	
	private MathUtil(){}
	
	/**
	 * <p>
	 * The bit shift of n.
	 * </p>
	 * 
	 * @param n
	 * @return the bit shift
	 * 
	 * @throws IllegalArgumentException if n not power of 2
	 */
	public final static int bitShift(int n) throws IllegalArgumentException {
		int shift = 0;
		for(int i = 0; i < 32; ++i) {
			if((n & 1) == 1) {
				if((n>>1) > 0) {
					throw new IllegalArgumentException("Argument must be power of 2: " + n);
				}
				shift = i;
				break;
			}
			n >>= 1;
		}
		return shift;
	}

}
