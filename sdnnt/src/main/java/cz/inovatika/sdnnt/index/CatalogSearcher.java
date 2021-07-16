/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.inovatika.sdnnt.index;

import cz.inovatika.sdnnt.Options;
import cz.inovatika.sdnnt.UserController;
import cz.inovatika.sdnnt.indexer.models.User;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.NoOpResponseParser;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class CatalogSearcher {

  public static final Logger LOGGER = Logger.getLogger(CatalogSearcher.class.getName());

  public JSONObject frbr(String id) {
    JSONObject ret = new JSONObject();
    try {
      SolrClient solr = Indexer.getClient();
      SolrQuery query = new SolrQuery("frbr:\"" + id + "\"");
      QueryRequest qreq = new QueryRequest(query);
      NoOpResponseParser rParser = new NoOpResponseParser();
      rParser.setWriterType("json");
      qreq.setResponseParser(rParser);
      NamedList<Object> qresp = solr.request(qreq, "catalog");
      ret = new JSONObject((String) qresp.get("response"));

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }


  public JSONObject search(Map<String, String> req, User user) {
    JSONObject ret = new JSONObject();
    try {
      SolrClient solr = Indexer.getClient();
      SolrQuery query = doQuery(req, user);
      QueryRequest qreq = new QueryRequest(query);
      NoOpResponseParser rParser = new NoOpResponseParser();
      rParser.setWriterType("json");
      qreq.setResponseParser(rParser);
      NamedList<Object> qresp = solr.request(qreq, "catalog");
      ret = new JSONObject((String) qresp.get("response"));
      if (ret.getJSONObject("response").getInt("numFound") > 0) {
        // Pridame info z zadosti
        List<String> ids = new ArrayList<>();
        for (Object o : ret.getJSONObject("response").getJSONArray("docs")) {
          JSONObject doc = (JSONObject) o;
          ids.add("\""+doc.getString("identifier")+"\"");
          //doc.put("zadost", findZadost(doc.getString("identifier")));
        }
        JSONArray zadosti = findZadosti(ids);
        ret.put("zadosti", zadosti);
      }

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }
  
  public JSONObject search(HttpServletRequest req) {
    Map<String, String[]> parameterMap = req.getParameterMap();
    Map<String, String>  resmap = new HashMap<>();
    parameterMap.entrySet().stream().forEach(stringEntry -> {
      resmap.put(stringEntry.getKey(), stringEntry.getValue()[0]);
    });
    User user = UserController.getUser(req);
    return search(resmap, user);
  }
  
  public JSONObject getA(Map<String, String> req, User user) {
    return getByStav(req, user, "A");
  }

  public JSONObject getA(HttpServletRequest req) {
    Map<String, String[]> parameterMap = req.getParameterMap();
    Map<String, String>  resmap = new HashMap<>();
    parameterMap.entrySet().stream().forEach(stringEntry -> {
      resmap.put(stringEntry.getKey(), stringEntry.getValue()[0]);
    });
    User user = UserController.getUser(req);
    return getA(resmap, user);
  }
  
  public JSONObject getPA(Map<String, String> req, User user) {
    return getByStav(req, user, "PA");
  }

  public JSONObject getPA(HttpServletRequest req) {
    Map<String, String[]> parameterMap = req.getParameterMap();
    Map<String, String>  resmap = new HashMap<>();
    parameterMap.entrySet().stream().forEach(stringEntry -> {
      resmap.put(stringEntry.getKey(), stringEntry.getValue()[0]);
    });
    User user = UserController.getUser(req);
    return getPA(resmap, user);
  }
  
  private JSONObject getByStav(Map<String, String> req, User user, String stav) {
    JSONObject ret = new JSONObject();
    try {
      SolrClient solr = Indexer.getClient();
      Options opts = Options.getInstance();
      int rows = opts.getClientConf().getInt("rows"); 
      if (req.containsKey("rows")) {
        rows = Integer.parseInt(req.get("rows"));
      }
      int start = 0; 
      if (req.containsKey("page")) {
        start = Integer.parseInt(req.get("page")) * rows;
      }
      SolrQuery query = new SolrQuery("*")
            .setRows(rows)
            .setStart(start) 
            .addFilterQuery("marc_990a:" + stav)
            .setSort("identifier", SolrQuery.ORDER.asc)
            .setFields("identifier,datum_stavu");
      QueryRequest qreq = new QueryRequest(query);
      NoOpResponseParser rParser = new NoOpResponseParser();
      rParser.setWriterType("json");
      qreq.setResponseParser(rParser);
      NamedList<Object> qresp = solr.request(qreq, "catalog");
      ret = new JSONObject((String) qresp.get("response"));

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  private JSONArray findZadosti(List<String> identifiers) {
    try {
      SolrClient solr = Indexer.getClient();
      String q = identifiers.toString().replace("[", "(").replace("]", ")").replaceAll(",", "");
           SolrQuery query = new SolrQuery("identifiers:" + q)
      //SolrQuery query = new SolrQuery("identifiers:\"" + identifier + "\"")
              .setFields("*,process:[json]");
  
      QueryRequest qreq = new QueryRequest(query);
      NoOpResponseParser rParser = new NoOpResponseParser();
      rParser.setWriterType("json");
      qreq.setResponseParser(rParser);
      NamedList<Object> qresp = solr.request(qreq, "zadost");
      return (new JSONObject((String) qresp.get("response"))).getJSONObject("response").getJSONArray("docs");
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
  }

  //  "filterFields": ["marc_990a", "item_type", "language", "marc_910a", "marc_856a", "nakladatel", "rokvydani"],
  private SolrQuery doQuery(HttpServletRequest req, User user) {
    Map<String, String> map = new HashMap<>();
    Enumeration<String> parameterNames = req.getParameterNames();
    while(parameterNames.hasMoreElements()) {
      String name = parameterNames.nextElement();
      String[] vals = req.getParameterMap().get(name);
      map.put(name, vals.length > 0 ? vals[0] : "");

    }
    return doQuery(map,user);
  }

  private SolrQuery doQuery(Map<String,String> req, User user) {
    String q = req.containsKey("q") ? req.get("q") : null;
    if (q == null) {
      q = "*";
    } else {
      q = ClientUtils.escapeQueryChars(q);
    }
    Options opts = Options.getInstance();
    int rows = opts.getClientConf().getInt("rows"); 
    if (req.containsKey("rows")) {
      rows = Integer.parseInt(req.get("rows"));
    }
    int start = 0; 
    if (req.containsKey("page")) {
      start = Integer.parseInt(req.get("page")) * rows;
    }
    SolrQuery query = new SolrQuery(q)
            .setRows(rows)
            .setStart(start) 
            .setFacet(true).addFacetField("item_type", "language", "marc_990a", "marc_910a", "nakladatel")
            .setFacetMinCount(1)
            .setParam("json.nl", "arrntv")
            .setParam("stats", true)
            .setParam("stats.field","rokvydani")
            .setParam("q.op", "AND")
            .setFields("*,raw:[json]");
    

    if (req.containsKey("q")) {
      query.setParam("df", "fullText");
    }
    if (req.containsKey("sort")) {
      query.setParam("sort", req.get("sort"));
    }
    
    for (Object o : opts.getClientConf().getJSONArray("filterFields")) {
      String field = (String) o;
      if (req.containsKey(field)) {
        if (field.equals("rokvydani")) {
          query.addFilterQuery(field + ":[" + req.get(field).replace(",", " TO ") + "]");
        } else {
          query.addFilterQuery(field + ":\"" + req.get(field) + "\"");
        }
      }
    }
    

      // Vseobecne filtry podle misto vydani (xr ) a roky
      
      // fq=fmt:BK%20AND%20place_of_pub:"xr%20"%20AND%20date1_int:%5B1910%20TO%202007%5D&fq=marc_338a:svazek&fq=-marc_245h:*&fq=marc_338b:nc&fq=marc_3382:rdacarrier
      int year = Calendar.getInstance().get(Calendar.YEAR);
      int fromYear = opts.getJSONObject("search").getInt("fromYear");
      int yearsBK = opts.getJSONObject("search").getInt("yearsBK");
      String bkDate = "((date1_int:["
              + fromYear 
              + " TO "
              + (year - yearsBK) 
              + "] AND -date2_int:*"
              + ") OR " + "(date1_int:["
              + fromYear 
              + " TO "
              + (year - yearsBK) 
              + "] AND date2_int:["
              + fromYear 
              + " TO "
              + (year - yearsBK) 
              + "]))";
      
      String bk = "(fmt:BK AND place_of_pub:\"xr \" AND "
              + bkDate 
              + " AND ((marc_338a:svazek AND marc_338b:nc AND marc_3382:rdacarrier) OR (-marc_245h:*)))";
      
      
      int yearsSE = opts.getJSONObject("search").getInt("yearsSE");
      String seDate = "((date1_int:["
              + fromYear 
              + " TO "
              + (year - yearsSE) 
              + "] AND date2_int:9999"
              + ") OR " + "date2_int:["
              + fromYear 
              + " TO "
              + (year - yearsSE) 
              + "])";
      String se = "(fmt:SE AND place_of_pub:\"xr \" AND "
              + seDate 
              + " AND ((marc_338a:svazek AND marc_338b:nc AND marc_3382:rdacarrier) OR (-marc_245h:*)))";
      
      
      query.addFilterQuery(bk + " OR " + se);
      
    // Filtry podle role
    //User user = UserController.getUser(req);
    if (!Boolean.parseBoolean(req.get("fullCatalog")) || user == null || "user".equals(user.role)) {
      // Filtrujeme defaultne kdyz neni parametr a kdyz je true
      // Z UI a podle user role
      query.addFilterQuery("-marc_990a:NNN");
    } else {
    }
     
    return query;
  }

}
