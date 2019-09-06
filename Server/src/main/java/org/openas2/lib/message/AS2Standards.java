package org.openas2.lib.message;

public class AS2Standards {
    // report multipart content type
    public static final String REPORT_SUBTYPE = "report; report-type=disposition-notification";
    public static final String REPORT_TYPE = "multipart/" + REPORT_SUBTYPE;
    // text part content header
    public static final String TEXT_TYPE = "text/plain";
    public static final String TEXT_CHARSET = "us-ascii";
    public static final String TEXT_ENCODING = "7bit";
    // disposition content header
    public static final String DISPOSITION_TYPE = "message/disposition-notification";
    public static final String DISPOSITION_CHARSET = "us-ascii";
    public static final String DISPOSITION_ENCODING = "7bit";

}
