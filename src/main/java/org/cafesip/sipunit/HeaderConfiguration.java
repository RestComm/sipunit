package org.cafesip.sipunit;

import javax.sip.header.*;
import java.util.List;

/**
 * Helper class for {@link SipPhone} registration process. Used to override the headers which will be generated
 * by the REGISTER request.
 * <p>
 * The purpose of this class is to provide an access to the underlying REGISTER request to test out different REGISTRATION
 * message forms. This way a separate SIP registrar component may be forced to produce an error, allowing the external
 * component to be integration tested with the desired REGISTER request header data.
 * <p>
 * Created by TELES AG on 12/06/2017.
 */
public class HeaderConfiguration {
	private ToHeader toHeader;
	private FromHeader fromHeader;

	private CallIdHeader callIdHeader;

	private CSeqHeader cSeqHeader;
	private MaxForwardsHeader maxForwardsHeader;

	private List<ViaHeader> viaHeaders;
	private ContactHeader contactHeader;

	private ExpiresHeader expiresHeader;

	public ToHeader getToHeader() {
		return toHeader;
	}

	public void setToHeader(ToHeader toHeader) {
		this.toHeader = toHeader;
	}

	public FromHeader getFromHeader() {
		return fromHeader;
	}

	public void setFromHeader(FromHeader fromHeader) {
		this.fromHeader = fromHeader;
	}

	public CallIdHeader getCallIdHeader() {
		return callIdHeader;
	}

	public void setCallIdHeader(CallIdHeader callIdHeader) {
		this.callIdHeader = callIdHeader;
	}

	public CSeqHeader getCSeqHeader() {
		return cSeqHeader;
	}

	public void setCSeqHeader(CSeqHeader cSeqHeader) {
		this.cSeqHeader = cSeqHeader;
	}

	public MaxForwardsHeader getMaxForwardsHeader() {
		return maxForwardsHeader;
	}

	public void setMaxForwardsHeader(MaxForwardsHeader maxForwardsHeader) {
		this.maxForwardsHeader = maxForwardsHeader;
	}

	public List<ViaHeader> getViaHeaders() {
		return viaHeaders;
	}

	public void setViaHeaders(List<ViaHeader> viaHeaders) {
		this.viaHeaders = viaHeaders;
	}

	public ContactHeader getContactHeader() {
		return contactHeader;
	}

	public void setContactHeader(ContactHeader contactHeader) {
		this.contactHeader = contactHeader;
	}

	public ExpiresHeader getExpiresHeader() {
		return expiresHeader;
	}

	public void setExpiresHeader(ExpiresHeader expiresHeader) {
		this.expiresHeader = expiresHeader;
	}
}
