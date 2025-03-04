package cz.inovatika.sdnnt.utils;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Utility methods for servlets */
public class ServletsSupport {

    private ServletsSupport() {}

    /** Generic method for reading json object from request */
    public static JSONObject readInputJSON(HttpServletRequest req) throws IOException {
      if (req.getMethod().equals("POST")) {
          String content = IOUtils.toString(req.getInputStream(), "UTF-8");
          return  new JSONObject(content);
      } else {
        return new JSONObject(req.getParameter("json"));
      }
    }
    
    
    public static JSONObject errorMissingParameterJson(HttpServletResponse response,  String parameter) {
        return errorJson(response, HttpServletResponse.SC_BAD_REQUEST, String.format("Missing parameter %s", parameter));
    } 
    
    /** Error json */
    public static JSONObject errorJson(HttpServletResponse response, int statusCode, String errorMessage) {
        // must handle on client side
        //if (statusCode != -1) response.setStatus(statusCode);
        if (response != null) response.setStatus(HttpServletResponse.SC_OK);
        JSONObject errorObject = new JSONObject();
        errorObject.put("error", errorMessage);
        return errorObject;
    }

    public static JSONObject errorJson(HttpServletResponse response, int statusCode, String key, String errorMessage) {
        if (response != null) response.setStatus(HttpServletResponse.SC_OK);
        JSONObject errorObject = new JSONObject();
        errorObject.put("error", errorMessage);
        errorObject.put("key", key);
        return errorObject;
    }

    public static JSONObject errorJson(HttpServletResponse response, int statusCode, String key, String errorMessage, JSONObject payload) {
        JSONObject errorObject = errorJson(response, statusCode, key, errorMessage);
        errorObject.put("payload", payload);
        return errorObject;
    }


    /** Error json */
    public static JSONObject errorJson(String errorMessage) {
        return errorJson(null, -1, errorMessage);
    }

}
