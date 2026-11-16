package javax.wireless.messaging;

public interface MultipartMessage extends Message {
    boolean addAddress(String type, String address);

    void addMessagePart(MessagePart part) throws SizeExceededException;

    String getAddress();

    String[] getAddresses(String type);

    String getHeader(String headerField);

    MessagePart getMessagePart(String contentID);

    MessagePart[] getMessageParts();

    String getStartContentId();

    String getSubject();

    boolean removeAddress(String type, String address);

    void removeAddresses();

    void removeAddresses(String type);

    boolean removeMessagePart(MessagePart part);

    boolean removeMessagePartId(String contentID);

    boolean removeMessagePartLocation(String contentLocation);

    void setAddress(String addr);

    void setHeader(String headerField, String headerValue);

    void setStartContentId(String contentId);

    void setSubject(String subject);
}
