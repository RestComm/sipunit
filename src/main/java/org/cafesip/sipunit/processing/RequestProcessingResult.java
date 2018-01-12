package org.cafesip.sipunit.processing;

/**
 * Contains provisional data on whether a request event was accepted by any {@link RequestProcessingStrategy} and if
 * that processing step was successful.
 * <p>
 * Created by TELES AG on 15/01/2018.
 *
 * @see RequestProcessor
 */
public class RequestProcessingResult {

	private final boolean isProcessed;
	private final boolean isSuccessful;

	/**
	 * @param isProcessed If the input was accepted and processed
	 * @param isSuccessful If the processing result was successful
	 */
	public RequestProcessingResult(boolean isProcessed, boolean isSuccessful) {
		this.isProcessed = isProcessed;
		this.isSuccessful = isSuccessful;
	}

	/**
	 * @return True if any strategy was able to accept the input of the request processor
	 */
	public boolean isProcessed() {
		return isProcessed;
	}

	/**
	 * @return True if the accepting strategy was able to successfully process the input of the request processor
	 */
	public boolean isSuccessful() {
		return isSuccessful;
	}
}
