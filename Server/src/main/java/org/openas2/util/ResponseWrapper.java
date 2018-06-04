package org.openas2.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.mail.internet.InternetHeaders;

import org.apache.http.HttpResponse;

public class ResponseWrapper {
	private String _transferTimeMs = "-1"; // amount of time in milliseconds taken to send a message and receive a response
	private int _statusCode = 0;
	private String _statusPhrase = null;
	private InternetHeaders _headers = new InternetHeaders();

	private String _body = null;

	public ResponseWrapper(HttpResponse response) {
		super();
		setStatusCode(response.getStatusLine().getStatusCode());
		setStatusPhrase(response.getStatusLine().getReasonPhrase());

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

			StringBuilder sb = new StringBuilder(200);
			String output;
			while ((output = br.readLine()) != null) {
				sb.append(output).append("\n");
			}

			setBody(sb.toString());
		} catch (Exception e) {
			setBody(e.toString());
		}
	}

	public InternetHeaders getHeaders() {
		return _headers;
	}

	public void setHeaders(InternetHeaders headers) {
		this._headers = headers;
	}

	public void addHeaderLine(String headerLine) {
		getHeaders().addHeaderLine(headerLine);
	}

	public String getTransferTimeMs() {
		return _transferTimeMs;
	}

	public void setTransferTimeMs(String transferTimeMs) {
		this._transferTimeMs = transferTimeMs;
	}

	public int getStatusCode() {
		return _statusCode;
	}

	protected void setStatusCode(int n) {
		this._statusCode = n;
	}

	public String getStatusPhrase() {
		return _statusPhrase;
	}

	public void setStatusPhrase(String statusPhrase) {
		this._statusPhrase = statusPhrase;
	}

	public String getBody() {
		return _body;
	}

	protected void setBody(String body) {
		this._body = body;
	}

}
