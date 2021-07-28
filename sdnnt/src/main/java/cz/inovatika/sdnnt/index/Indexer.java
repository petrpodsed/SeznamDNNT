/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.inovatika.sdnnt.index;

import cz.inovatika.sdnnt.Options;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import com.flipkart.zjsonpatch.JsonPatch;
import cz.inovatika.sdnnt.indexer.models.Import;
import cz.inovatika.sdnnt.indexer.models.MarcRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CursorMarkParams;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class Indexer {

  public static final Logger LOGGER = Logger.getLogger(Indexer.class.getName());

  static List<String> dntSetFields = Arrays.asList("/dataFields/990", "/dataFields/992", "/dataFields/998");
  static List<String> identifierFields = Arrays.asList("/identifier", "/datestamp", "/setSpec",
          "/controlFields/001", "/controlFields/003", "/controlFields/005", "/controlFields/008");

  private static SolrClient solr;

  public static SolrClient getClient() {
    if (solr == null) {
      solr = new HttpSolrClient.Builder(Options.getInstance().getString("solr.host", "http://localhost:8983/solr/")).build();
    }
    return solr;
  }

  public static void closeClient() {
    try {
      solr.close();
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
  }

  public static JSONObject add(String collection, List<SolrInputDocument> recs, boolean merge, boolean update, String user) {
    JSONObject ret = new JSONObject();
    try {
      if (update) {
        for (SolrInputDocument rec : recs) {
          SolrDocumentList docs = findById((String) rec.getFieldValue("identifier"));
          if (docs.getNumFound() == 0) {
            LOGGER.log(Level.FINE, "Record " + rec.getFieldValue("identifier") + " not found in catalog. It is new");
            getClient().add("catalog", rec);
          } else {
            SolrInputDocument hDoc = new SolrInputDocument();
            SolrInputDocument cDoc = mergeWithHistory(
                    (String) rec.getFieldValue("raw"),
                    docs.get(0), hDoc,
                    user, update, ret);
            if (cDoc != null) {
              getClient().add("catalog", cDoc);
              getClient().add("history", hDoc);
            }
          }

        }
      } else if (merge) {
        for (SolrInputDocument rec : recs) {
          SolrDocumentList docs = find((String) rec.getFieldValue("raw"));
          if (docs == null) {

          } else if (docs.getNumFound() == 0) {
            LOGGER.log(Level.WARNING, "Record " + rec.getFieldValue("identifier") + " not found in catalog");
            ret.append("errors", "Record " + rec.getFieldValue("identifier") + " not found in catalog");
          } else if (docs.getNumFound() > 1) {
            LOGGER.log(Level.WARNING, "For" + rec.getFieldValue("identifier") + " found more than one record in catalog: " + docs.stream().map(d -> (String) d.getFirstValue("identifier")).collect(Collectors.joining()));
            ret.append("errors", "For" + rec.getFieldValue("identifier") + " found more than one record in catalog: " + docs.stream().map(d -> (String) d.getFirstValue("identifier")).collect(Collectors.joining()));
          }

          List<SolrInputDocument> hDocs = new ArrayList();
          List<SolrInputDocument> cDocs = new ArrayList();
          for (SolrDocument doc : docs) {
            SolrInputDocument hDoc = new SolrInputDocument();

            SolrInputDocument cDoc = mergeWithHistory(
                    (String) rec.getFieldValue("raw"),
                    doc, hDoc,
                    user, update, ret);
            if (cDoc != null) {
              hDocs.add(hDoc);
              cDocs.add(cDoc);
            }
          }

          if (!cDocs.isEmpty()) {
            getClient().add("history", hDocs);
            getClient().add("catalog", cDocs);
            hDocs.clear();
            cDocs.clear();
          }

        }
      } else {
        getClient().add(collection, recs);
      }
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
    }
    return ret;
  }

  // keepDNTFields = true => zmena vsech poli krome DNT (990, 992)
  // keepDNTFields = false => zmena pouze DNT (990, 992) poli
  private static SolrInputDocument mergeWithHistory(String jsTarget,
          SolrDocument docCat, SolrInputDocument historyDoc,
          String user, boolean keepDNTFields, JSONObject ret) {

    try {
      String jsCat = (String) docCat.getFirstValue("raw");

      ObjectMapper mapper = new ObjectMapper();
      JsonNode source = mapper.readTree(jsCat);

      // As target the sdnnt record
      JsonNode target = mapper.readTree(jsTarget);

      JsonNode fwPatch = JsonDiff.asJson(source, target);
      removeOpsForDNTFields(fwPatch, keepDNTFields);
      if (new JSONArray(fwPatch.toString()).length() > 0) {
        JsonNode bwPatch = JsonDiff.asJson(target, source);
        removeOpsForDNTFields(bwPatch, keepDNTFields);

        historyDoc.setField("identifier", docCat.getFirstValue("identifier"));
        historyDoc.setField("user", user);
        historyDoc.setField("type", "app");
        JSONObject changes = new JSONObject()
                .put("forward_patch", new JSONArray(fwPatch.toString()))
                .put("backward_patch", new JSONArray(bwPatch.toString()));
        historyDoc.setField("changes", changes.toString());

        // Create record in catalog
        MarcRecord mr = MarcRecord.fromJSON(JsonPatch.apply(fwPatch, source).toString());
        // mr.fillSolrDoc();
        return mr.toSolrDoc();
      } else {
        LOGGER.log(Level.FINE, "No changes detected in {0}", target.at("/identifier").asText());
        return null;
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "error merging {0}", docCat.getFirstValue("identifier"));
      LOGGER.log(Level.SEVERE, null, ex);
      ret.append("errors", "error merging " + docCat.getFirstValue("identifier"));
      return null;
    }
  }

  public static SolrDocumentList findById(String identifier) {
    // JSONObject ret = new JSONObject();
    try {
      SolrQuery query = new SolrQuery("*")
              .addFilterQuery("identifier:\"" + identifier + "\"")
              .setRows(1)
              .setFields("*");
      return getClient().query("catalog", query).getResults();

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
    // return ret;
  }

  public static SolrDocumentList find(String source) {
    // JSONObject ret = new JSONObject();
    try {

      MarcRecord mr = MarcRecord.fromJSON(source);
      mr.toSolrDoc();
      String q = "(controlfield_001:\"" + mr.sdoc.getFieldValue("controlfield_001") + "\""
              + " AND marc_040a:\"" + mr.sdoc.getFieldValue("marc_040a") + "\""
              + " AND controlfield_008:\"" + mr.sdoc.getFieldValue("controlfield_008") + "\")"
              + " OR marc_020a:\"" + mr.sdoc.getFieldValue("marc_020a") + "\""
              + " OR marc_015a:\"" + mr.sdoc.getFieldValue("marc_015a") + "\""
              + " OR dedup_fields:\"" + mr.sdoc.getFieldValue("dedup_fields") + "\"";

      SolrQuery query = new SolrQuery(q)
              .setRows(20)
              .setFields("*,score");
      return getClient().query("catalog", query).getResults();
//      QueryRequest qreq = new QueryRequest(query);
//      NoOpResponseParser rParser = new NoOpResponseParser();
//      rParser.setWriterType("json");
//      qreq.setResponseParser(rParser);
//      NamedList<Object> qresp = solr.request(qreq, "catalog");
//      return new JSONObject((String) qresp.get("response"));

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }
    // return ret;
  }

  public static JSONObject approveInImport(String identifier, String newRaw, String user) {
    JSONObject ret = new JSONObject();
    try {

      Indexer.changeStav(identifier, "VVS", user);
      Import impNew = Import.fromJSON(newRaw);
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("id:\"" + impNew.id + "\"");
      Import impOld = getClient().query("imports", q).getBeans(Import.class).get(0);
      History.log(identifier, impOld.toJSONString(), impNew.toJSONString(), user, "import");

      // Update record in imports
      ret = Import.approve(impNew, identifier, user);

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public static JSONObject changeStav(String identifier, String navrh, String user) {
    JSONObject ret = new JSONObject();
    try {
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("identifier:\"" + identifier + "\"")
              .setFields("raw, marc_990a");
      SolrDocument docOld = getClient().query("catalog", q).getResults().get(0);
      String oldRaw = (String) docOld.getFirstValue("raw");
      Collection<Object> oldStav = docOld.getFieldValues("marc_990a");

      MarcRecord mr = MarcRecord.fromJSON(oldRaw);
      mr.toSolrDoc();
      if (navrh.equals("VVS")) {
        if (oldStav == null || oldStav.contains("A")) {
          mr.setStav("VS");
        } else if (oldStav.contains("PA")) {
          mr.setStav("VN");
        }

      } else if (navrh.equals("NZN")) {
        mr.setStav("A");
      }

      mr.toSolrDoc(true);
      History.log(identifier, oldRaw, mr.toJSON().toString(), user, "catalog");

      // Update record in catalog
      getClient().add("catalog", mr.sdoc);
      getClient().commit("catalog");

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  /**
   * Save record in catalog. Generates diff path and index to history core We
   * store the patch in both orders, forward and backwards
   *
   * @param id identifier in catalog
   * @param newRaw JSON representation of the record
   * @param user User
   * @return
   */
  public JSONObject save(String id, JSONObject newRaw, String user) {
    Options opts = Options.getInstance();
    JSONObject ret = new JSONObject();
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host", "http://localhost:8983/solr/")).build()) {
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("identifier:\"" + id + "\"")
              .setFields("raw");
      SolrDocument docOld = solr.query("catalog", q).getResults().get(0);
      String oldRaw = (String) docOld.getFirstValue("raw");

      History.log(id, oldRaw, newRaw.toString(), user, "catalog");

      // Update record in catalog
      MarcRecord mr = MarcRecord.fromJSON(newRaw.toString());
      //mr.toSolrDoc();
      solr.add("catalog", mr.toSolrDoc());
      solr.commit("catalog");

      ret = new JSONObject(newRaw);
      solr.close();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public static JSONObject reindex(String dest, String filter, String collection, boolean cleanStav) {
    JSONObject ret = new JSONObject();
    int indexed = 0;
    try (SolrClient solr = new ConcurrentUpdateSolrClient.Builder(dest).build()) {
      String cursorMark = CursorMarkParams.CURSOR_MARK_START;
      SolrQuery q = new SolrQuery("*").setRows(1000)
              .setSort("identifier", SolrQuery.ORDER.desc)
              .addFilterQuery(filter)
              .setFields("raw");
      List<SolrInputDocument> idocs = new ArrayList<>();
      boolean done = false;
      while (!done) {
        q.setParam(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
        QueryResponse qr = getClient().query(collection, q);
        String nextCursorMark = qr.getNextCursorMark();
        SolrDocumentList docs = qr.getResults();
        for (SolrDocument doc : docs) {
          String oldRaw = (String) doc.getFirstValue("raw");

          MarcRecord mr = MarcRecord.fromJSON(oldRaw);
          if (cleanStav) {
            mr.dataFields.remove("990");
            mr.dataFields.remove("992");
          }
          idocs.add(mr.toSolrDoc());
        }

        if (!idocs.isEmpty()) {
          solr.add(collection, idocs);
          solr.commit(collection);
          indexed += idocs.size();
          idocs.clear();
          LOGGER.log(Level.INFO, "Curently reindexed: {0}", indexed);
        }
        if (cursorMark.equals(nextCursorMark)) {
          done = true;
        }
        cursorMark = nextCursorMark;
      }
      solr.commit(collection);
      solr.close();
      LOGGER.log(Level.INFO, "Reindex finished: {0}", indexed);
      ret.put("reindex", indexed);
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  public static JSONObject reindexId(String id) {
    JSONObject ret = new JSONObject();
    try {
      SolrClient solr = getClient();
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("identifier:\"" + id + "\"")
              .setFields("raw");
      SolrDocument docOld = solr.query("catalog", q).getResults().get(0);
      String oldRaw = (String) docOld.getFirstValue("raw");

      // Update record in catalog
      MarcRecord mr = MarcRecord.fromJSON(oldRaw);
      solr.add("catalog", mr.toSolrDoc());
      solr.commit("catalog");
      ret = mr.toJSON();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  /**
   * Compares record from DNT-ALL set (in sdnnt core) with SKC record (catalog
   * core)
   *
   * @param id
   * @return
   */
  public JSONObject compare(String id) {
    Options opts = Options.getInstance();
    JSONObject ret = new JSONObject();
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host", "http://localhost:8983/solr/")).build()) {
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("identifier:" + id)
              .setFields("marc_035a,raw");
      SolrDocument docDnt = solr.query("sdnnt", q).getResults().get(0);
      String sysno = (String) docDnt.getFirstValue("marc_035a");
      String jsDnt = (String) docDnt.getFirstValue("raw");
      sysno = sysno.substring(sysno.lastIndexOf(")") + 1);

      SolrQuery q2 = new SolrQuery("*").setRows(1)
              .addFilterQuery("controlfield_001:" + sysno)
              .setFields("raw");
      SolrDocument docCat = solr.query("catalog", q2).getResults().get(0);
      String jsCat = (String) docCat.getFirstValue("raw");
      ObjectMapper mapper = new ObjectMapper();
      JsonNode source = mapper.readTree(jsCat);
      JsonNode target = mapper.readTree(jsDnt);

      JsonNode patch = JsonDiff.asJson(source, target);
      ret.put("diff", new JSONArray(patch.toString()));
      removeOpsForDNTFields(patch, false);
      ret.put("catalog", new JSONObject(jsCat));
      ret.put("sdnnt", new JSONObject(jsDnt));
      ret.put("patch", new JSONArray(patch.toString()));

      ret.put("catalog_new", new JSONObject(JsonPatch.apply(patch, source).toString()));
      solr.close();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  /**
   * Compares two records
   *
   * @param sourceRaw
   * @param targetRaw
   * @return
   */
  public static JSONObject compare(String sourceRaw, String targetRaw) {
    JSONObject ret = new JSONObject();
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode source = mapper.readTree(sourceRaw);
      JsonNode target = mapper.readTree(targetRaw);

      JsonNode patch = JsonDiff.asJson(source, target);
      removeOpsForIdenfiersFields(patch);
      ret.put("diff", new JSONArray(patch.toString()));
      // ret.put("patch", new JSONArray(patch.toString()));

    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  /**
   * Merges record from source core with SKC record (catalog core)
   *
   * @param sourceCore Core to merge from, i.e. sdnnt
   * @param id identifier in source core
   * @param user User
   * @return
   */
  public JSONObject mergeId(String sourceCore, String id, String user) {
    Options opts = Options.getInstance();
    JSONObject ret = new JSONObject();
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host", "http://localhost:8983/solr/")).build()) {
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("identifier:" + id)
              .setFields("marc_035a,raw");

      SolrDocumentList docs = solr.query(sourceCore, q).getResults();
      if (docs.getNumFound() == 0) {
        ret.put("error", "Record not found in sndt");
        return ret;
      }
      SolrDocument docDnt = docs.get(0);
      String sysno = (String) docDnt.getFirstValue("marc_035a");
      String jsDnt = (String) docDnt.getFirstValue("raw");
      sysno = sysno.substring(sysno.lastIndexOf(")") + 1);

      SolrQuery q2 = new SolrQuery("*").setRows(1)
              .addFilterQuery("controlfield_001:" + sysno)
              .setFields("identifier,raw");
      SolrDocumentList docsCat = solr.query("catalog", q2).getResults();
      if (docsCat.getNumFound() == 0) {
        ret.append("errors", "Record " + id + " not found in catalog");
        return ret;
      } else if (docsCat.getNumFound() > 1) {
        ret.append("errors", "Found more than one record in catalog: " + docsCat.stream().map(d -> (String) d.getFirstValue("identifier")).collect(Collectors.joining()));
        return ret;
      } else {
        SolrDocument docCat = docsCat.get(0);
        String jsCat = (String) docCat.getFirstValue("raw");

        // addToHistory(id, jsCat, jsDnt, user, "app");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode source = mapper.readTree(jsCat);

        // As target the sdnnt record
        JsonNode target = mapper.readTree(jsDnt);
        JsonNode fwPatch = JsonDiff.asJson(source, target);
        removeOpsForDNTFields(fwPatch, false);
        JsonNode bwPatch = JsonDiff.asJson(target, source);
        removeOpsForDNTFields(bwPatch, false);

        ret.put("forward_patch", new JSONArray(fwPatch.toString()));
        ret.put("backward_patch", new JSONArray(bwPatch.toString()));

        // Insert in history
        SolrInputDocument idoc = new SolrInputDocument();
        idoc.setField("identifier", id);
        idoc.setField("user", user);
        idoc.setField("type", "app");
        idoc.setField("changes", ret.toString());
        solr.add("history", idoc);
        solr.commit("history");

        // Update record in catalog
        //      MarcRecord mr = MarcRecord.fromJSON(newRaw);      
        //      mr.fillSolrDoc();
        //      solr.add("catalog", mr.toSolrDoc());
        //      solr.commit("catalog");
        ret.put("catalog_new", new JSONObject(JsonPatch.apply(fwPatch, source).toString()));
      }
      solr.close();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  /**
   * Merges records if source core with SKC records (catalog core)
   *
   * @param sourceCore Core to merge from, i.e. sdnnt
   * @param user User
   * @return
   */
  public JSONObject mergeCore(String sourceCore, String user) {
    return mergeCore(sourceCore, user, null);
  }

  public JSONObject mergeCore(String sourceCore, String user, String from) {
    long start = new Date().getTime();
    Options opts = Options.getInstance();
    JSONObject ret = new JSONObject();
    List<SolrInputDocument> hDocs = new ArrayList();
    List<SolrInputDocument> cDocs = new ArrayList();
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host", "http://localhost:8983/solr/")).build()) {
      int indexed = 0;
      String cursorMark = CursorMarkParams.CURSOR_MARK_START;
      SolrQuery q = new SolrQuery("*").setRows(1000)
              .setSort("identifier", SolrQuery.ORDER.asc)
              .setFields("identifier,raw");
      if (from != null) {
        q.addFilterQuery("datestamp:[" + from + " TO NOW]");
      }
      boolean done = false;
      List<SolrInputDocument> idocs = new ArrayList<>();
      while (!done) {
        q.setParam(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
        QueryResponse qr = solr.query(sourceCore, q);
        String nextCursorMark = qr.getNextCursorMark();
        SolrDocumentList docs = qr.getResults();
        for (SolrDocument doc : docs) {

          SolrInputDocument idoc = new SolrInputDocument();

          for (String name : doc.getFieldNames()) {
            idoc.addField(name, doc.getFieldValue(name));
          }
          idocs.add(idoc);

//            String oldRaw = (String) doc.getFirstValue("raw");
//            MarcRecord mr = MarcRecord.fromJSON(oldRaw);
//            idocs.add(mr.toSolrDoc());
        }

        add("catalog", idocs, true, false, user);
        idocs.clear();
//        for (SolrDocument doc : docs) 
//          // if (doc.getFirstValue("marc_035a") != null) {
//            SolrInputDocument hDoc = new SolrInputDocument();
//            SolrInputDocument cDoc = mergeWithHistory((String) doc.getFirstValue("raw"), doc, hDoc, user, false, ret);
//            if (cDoc != null) {
//              hDocs.add(hDoc);
//              cDocs.add(cDoc);
//            }
//          // }
//        }
//
//        if (!cDocs.isEmpty()) {
////          solr.add("history", hDocs);
//          solr.add("catalog", cDocs);
//          hDocs.clear();
//          cDocs.clear();
//        }
        indexed += docs.size();
        if (cursorMark.equals(nextCursorMark)) {
          done = true;
        }
        cursorMark = nextCursorMark;
        LOGGER.log(Level.INFO, "Current indexed: {0}", indexed);
      }
      // solr.commit("history");
      solr.commit("catalog");
      ret.put("indexed", indexed);
      String ellapsed = DurationFormatUtils.formatDurationHMS(new Date().getTime() - start);
      ret.put("ellapsed", ellapsed);
      solr.close();
      LOGGER.log(Level.INFO, "mergecore FINISHED. Indexed {0} in {1}", new Object[]{ellapsed, indexed});
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }

  /**
   *
   * @param solr
   * @param jsDnt
   * @param identifier
   * @param sysno
   * @param sigla
   * @param controlfield_008
   * @param dedup
   * @param user
   * @param historyDoc
   * @param ret
   * @return doc for catalog
   */
  private static SolrInputDocument mergeRaw2(String jsDnt,
          String identifier, String sysno, String sigla, String controlfield_008,
          String dedup,
          String user, SolrInputDocument historyDoc,
          JSONObject ret) {
    try {
      if (jsDnt == null) {
        LOGGER.log(Level.WARNING, "Record {0} has empty raw", identifier);
        return null;
      }
      SolrQuery q2 = new SolrQuery("*").setRows(1)
              .addFilterQuery("controlfield_001:\"" + sysno.substring(sysno.lastIndexOf(")") + 1) + "\"")
              .addFilterQuery("marc_040a:" + sigla)
              .addFilterQuery("controlfield_008:\"" + controlfield_008 + "\"")
              .setFields("identifier,raw");
      SolrDocumentList docsCat = getClient().query("catalog", q2).getResults();
      if (docsCat.getNumFound() == 0) {
        // ret.append("errors", "Record " + identifier + " with sysno: " + sysno + " not found in catalog");
        LOGGER.log(Level.WARNING, "Record {0} with sysno: {1} not found in catalog", new Object[]{identifier, sysno});
        return null;
      } else if (docsCat.getNumFound() > 1) {
        LOGGER.log(Level.WARNING, "Found more than one record in catalog: {0} -> {1}", new Object[]{identifier, docsCat.stream().map(d -> (String) d.getFirstValue("identifier")).collect(Collectors.joining())});
        // ret.append("errors", "Found more than one record in catalog: " + identifier + " with sysno: " + sysno);
        return null;
      }
      SolrDocument docCat = docsCat.get(0);
      String jsCat = (String) docCat.getFirstValue("raw");

      ObjectMapper mapper = new ObjectMapper();
      JsonNode source = mapper.readTree(jsCat);

      // As target the sdnnt record
      JsonNode target = mapper.readTree(jsDnt);

      JsonNode fwPatch = JsonDiff.asJson(source, target);
      removeOpsForDNTFields(fwPatch, false);
      JsonNode bwPatch = JsonDiff.asJson(target, source);
      removeOpsForDNTFields(bwPatch, false);

//      ret.put("forward_patch", new JSONArray(fwPatch.toString()));
//      ret.put("backward_patch", new JSONArray(bwPatch.toString()));
      historyDoc.setField("identifier", docCat.getFirstValue("identifier"));
      historyDoc.setField("user", user);
      historyDoc.setField("type", "app");
      historyDoc.setField("changes", ret.toString());

      // Create record in catalog
      MarcRecord mr = MarcRecord.fromJSON(JsonPatch.apply(fwPatch, source).toString());
      return mr.toSolrDoc();

    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.append("errors", ex);
      return null;
    }
  }

  // keepDNTFields = true => zmena vsech poli krome DNT (990, 992)
  // keepDNTFields = false => zmena pouze DNT (990, 992) poli
  private static void removeOpsForDNTFields(Iterable jsonPatch, boolean keep) {
    Iterator<JsonNode> patchIterator = jsonPatch.iterator();
    while (patchIterator.hasNext()) {
      JsonNode patchOperation = patchIterator.next();
      // JsonNode operationType = patchOperation.get("op");
      JsonNode pathName = patchOperation.get("path");
      // if (operationType.asText().equals("replace") && ignoredFields.contains(pathName.asText())) {
      if (!dntSetFields.contains(pathName.asText()) && !keep) {
        patchIterator.remove();
      } else if (dntSetFields.contains(pathName.asText()) && keep) {
        patchIterator.remove();
      }
    }
  }

  private static void removeOpsForIdenfiersFields(Iterable jsonPatch) {
    Iterator<JsonNode> patchIterator = jsonPatch.iterator();
    while (patchIterator.hasNext()) {
      JsonNode patchOperation = patchIterator.next();
      JsonNode operationType = patchOperation.get("op");
      JsonNode pathName = patchOperation.get("path");
      // if (operationType.asText().equals("replace") && ignoredFields.contains(pathName.asText())) {
      if (identifierFields.contains(pathName.asText())) {
        patchIterator.remove();
      }
    }
  }

  public JSONObject test(String sourceCore, String id) {
    Options opts = Options.getInstance();
    JSONObject ret = new JSONObject();
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host")).build()) {
      SolrQuery q = new SolrQuery("*").setRows(1)
              .addFilterQuery("identifier:" + id)
              .setFields("raw");

      SolrDocumentList docs = solr.query(sourceCore, q).getResults();
      if (docs.getNumFound() == 0) {
        ret.put("error", "Record not found in " + sourceCore);
        return ret;
      }
      SolrDocument docDnt = docs.get(0);
      String json = (String) docDnt.getFirstValue("raw");
      MarcRecord mr = MarcRecord.fromJSON(json);
      // mr.fillSolrDoc();

      ObjectMapper mapper = new ObjectMapper();
      JsonNode source = mapper.readTree(json);

      JsonNode target = mapper.readTree(mr.toJSON().toString());
      JsonNode fwPatch = JsonDiff.asJson(source, target);

      ret.put("diff", new JSONArray(fwPatch.toString()));
      ret.put("raw", mr.toJSON());

      solr.close();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      ret.put("error", ex);
    }
    return ret;
  }
}
