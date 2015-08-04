package org.openas2.lib.message;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;


public class MessageID {
	private static Random rndGen;
	private Date timeStamp;
	private String receiverID;
	private String senderID;
	private String uniqueStamp;

	protected synchronized Random getRandomGenerator() {
		if (rndGen == null) {
			rndGen = new Random();
		}
		return rndGen;
	}
	
	public MessageID(String senderID, String receiverID) {
		super();
		this.senderID = senderID;
		this.receiverID = receiverID;
	}

	public void setReceiverID(String receiver) {
		receiverID = receiver;
	}

	public String getReceiverID() {
		return receiverID;
	}

	public void setSenderID(String sender) {
		senderID = sender;
	}

	public String getSenderID() {
		return senderID;
	}

	public void setTimeStamp(Date timeStamp) {
		this.timeStamp = timeStamp;
	}

	public Date getTimeStamp() {
		if (timeStamp == null) {
			timeStamp = new Date();
		}

		return timeStamp;
	}

	public void setUniqueStamp(String uniqueStamp) {
		this.uniqueStamp = uniqueStamp;
	}

	public String getUniqueStamp() {
		if (uniqueStamp == null) {
			DecimalFormat randomFormatter = new DecimalFormat("0000");
			
			uniqueStamp = randomFormatter.format(getRandomGenerator().nextInt(10000));
		}

		return uniqueStamp;
	}

	public String toString() {
		StringBuffer buf = new StringBuffer();
		SimpleDateFormat formatter = new SimpleDateFormat("ddMMyyyyHHmmssZ");
		buf.append("<OPENAS2-").append(formatter.format(getTimeStamp()));
		buf.append("-").append(getUniqueStamp());

		if ((getSenderID() != null) || (getReceiverID() != null)) {
			buf.append("@").append(getSenderID());
			buf.append("_").append(getReceiverID());
		}

		buf.append(">");

		return buf.toString();
	}
}
