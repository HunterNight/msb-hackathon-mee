package com.app.service.security;

import com.app.constant.MiConstants;
import org.springframework.stereotype.Component;

/**
 * Everything that did not come from the system prompt is wrapped and labelled before the model
 * sees it, and the wrapper tags in the content itself are escaped so nothing can close the block
 * early (design §12.1).
 */
@Component
public class UntrustedWrapper {

  public String wrap(String source, String content) {
    return wrap(source, null, content);
  }

  public String wrap(String source, String name, String content) {
    String attribute = name == null ? "" : " doc=\"" + escapeAttribute(name) + "\"";
    return "<"
        + MiConstants.UNTRUSTED_TAG
        + " source=\""
        + escapeAttribute(source)
        + "\""
        + attribute
        + ">"
        + escape(content)
        + "</"
        + MiConstants.UNTRUSTED_TAG
        + ">";
  }

  /** Escapes the angle brackets so a crafted message cannot forge a closing tag. */
  public String escape(String content) {
    return content == null ? "" : content.replace("<", "&lt;").replace(">", "&gt;");
  }

  private String escapeAttribute(String value) {
    return value.replace("\"", "'").replace("<", "").replace(">", "");
  }
}
