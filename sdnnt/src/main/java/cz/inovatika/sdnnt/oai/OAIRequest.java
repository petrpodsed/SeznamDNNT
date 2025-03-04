/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.inovatika.sdnnt.oai;

import cz.inovatika.sdnnt.Options;
import static cz.inovatika.sdnnt.index.Indexer.getClient;
import cz.inovatika.sdnnt.indexer.models.MarcRecord;
import cz.inovatika.sdnnt.indexer.models.oai.OAIMetadataFormat;

import static cz.inovatika.sdnnt.oai.OAIServlet.LOGGER;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import cz.inovatika.sdnnt.model.DataCollections;
import cz.inovatika.sdnnt.model.PublicItemState;
import cz.inovatika.sdnnt.utils.StringUtils;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class OAIRequest {
    
   private static final String METADATA_PREFIX_PARAMETER = "metadataPrefix";

public static String SDNNT_PREFIX_IDENTIFIER = "sdnnt.nkp.cz";
    
  // Muze byt indextime nebo datestamp
  static String SORT_FIELD = "indextime";
  static String CURSOR_FIELD = "identifier";

  public static String headerOAI() {
    return "<OAI-PMH xmlns=\"http://www.openarchives.org/OAI/2.0/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd\">\n";
  }

  public static String responseDateTag() {
    return "<responseDate>" + ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_INSTANT) + "</responseDate>";
  }

  public static String requestTag(HttpServletRequest req) {
    StringBuilder ret = new StringBuilder();
    ret.append("<request ");
    for (String p : req.getParameterMap().keySet()) {
      ret.append(p).append("=\"").append(req.getParameter(p)).append("\" ");
    }
    ret.append(">").append(req.getRequestURL()).append("</request>");
    return ret.toString();
  }

  public static String identify(HttpServletRequest req) {
    JSONObject conf = Options.getInstance().getJSONObject("OAI");
    String xml = headerOAI() + responseDateTag() + requestTag(req)
            + "<Identify>"
            + "<repositoryName>" + conf.getString("repositoryName") + "</repositoryName>"
            + "<baseURL>" + req.getRequestURL() + "</baseURL>"
            + "<protocolVersion>2.0</protocolVersion>"
            + "<adminEmail>" + conf.getString("adminEmail") + "</adminEmail>"
            + "<earliestDatestamp>2012-06-30T22:26:40Z</earliestDatestamp>"
            + "<deletedRecord>persistent</deletedRecord>"
            + "<granularity>YYYY-MM-DDThh:mm:ssZ</granularity>"
            + "<description>"
            + "<oai-identifier xmlns=\"http://www.openarchives.org/OAI/2.0/oai-identifier\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd\">"
            + "<scheme>oai</scheme>"
            + "<repositoryIdentifier>aleph-nkp.cz</repositoryIdentifier>"
            + "<delimiter>:</delimiter>"
            + "<sampleIdentifier>oai:aleph-nkp.cz:NKC01-000000001</sampleIdentifier>"
            + "</oai-identifier>"
            + "</description>"
            + "</Identify>"
            + "</OAI-PMH>";
    return xml;
  }

  public static String listSets(HttpServletRequest req) {
    String xml = headerOAI() + responseDateTag() + requestTag(req)
            + "<ListSets>";
    JSONObject sets = Options.getInstance().getJSONObject("OAI").getJSONObject("sets");
    for (Object spec: sets.keySet()) {
      JSONObject set = sets.getJSONObject((String) spec);
      xml += "<set><setSpec>"+((StringUtils.isAnyString(spec.toString()) ?  spec.toString():"none")) +"</setSpec><setName>"+set.getString("name")+"</setName></set>\n";
    }
    xml += "</ListSets>\n"
           + "</OAI-PMH>";
    return xml;
  }

  public static String metadataFormats(HttpServletRequest req) {

      String prefixes = Arrays.asList(OAIMetadataFormat.values()).stream().map(f-> {
          StringBuilder builder = new StringBuilder("<metadataFormat>");
          builder.append( String.format("<metadataPrefix>%s</metadataPrefix>", f.name()));
          if (f.getSchema() != null) {
              builder.append(String.format("<schema>%s</schema>", f.getSchema()));
          }
          if (f.getDefaultNamespace() != null) {
              builder.append(String.format("<metadataNamespace>%s</metadataNamespace>", f.getDefaultNamespace()));
          }
          builder.append("</metadataFormat>");
          return builder.toString();
      }).collect(Collectors.joining("\n"));
      
      
      String xml = headerOAI() + responseDateTag() + requestTag(req)+String.format("<ListMetadataFormats>%s</ListMetadataFormats>", prefixes)+ "</OAI-PMH>";
//      + "<ListMetadataFormats>"
//            + "<metadataFormat><metadataPrefix>marc21</metadataPrefix><schema>http://www.loc.gov/standards/marcxml/schema/MARC21slim.xsd</schema>"
//            + "<metadataNamespace>http://www.loc.gov/MARC21/slim</metadataNamespace>"
//            + "</metadataFormat>"
//            + "</ListMetadataFormats>"
//            + "</OAI-PMH>";

      return xml;
  }

  public static String listRecords(HttpServletRequest req, boolean onlyIdentifiers) {
    String verb = onlyIdentifiers ? "ListIdentifiers" : "ListRecords";
    Options opts = Options.getInstance();
    StringBuilder ret = new StringBuilder();
    int rows = opts.getJSONObject("OAI").getInt("rowsPerPage");
    ret.append(headerOAI())
            .append(responseDateTag())
            .append(requestTag(req));
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host")).build()) {
      
      String cursorMark = CursorMarkParams.CURSOR_MARK_START;
      SolrQuery query = new SolrQuery("*")
              .setRows(rows)
              .addSort(SORT_FIELD, SolrQuery.ORDER.asc)
              .addSort(CURSOR_FIELD, SolrQuery.ORDER.asc)
              .setFields(SORT_FIELD, "identifier,id_sdnnt,raw,dntstav,datum_stavu,license,license_history,historie_stavu,granularity,masterlinks,masterlinks_disabled,digital_libraries");
      if (req.getParameter("from") != null) {
        String from = req.getParameter("from");
        String until = "*";
        if (req.getParameter("until") != null) {
          until = req.getParameter("until");
        }
        query.addFilterQuery(SORT_FIELD + ":[" + from + " TO " + until + "]");
      }

      String set = req.getParameter("set");
      if (set != null) {
          query.addFilterQuery(Options.getInstance().getJSONObject("OAI").getJSONObject("sets").getJSONObject(set).getString("filter"));
      } 
      
      OAIMetadataFormat format = OAIMetadataFormat.marc21;
      if (StringUtils.isAnyString(req.getParameter(METADATA_PREFIX_PARAMETER))) {
          format = OAIMetadataFormat.valueOf(req.getParameter(METADATA_PREFIX_PARAMETER));
      }

      
      if (req.getParameter("resumptionToken") != null) {
          String rt = req.getParameter("resumptionToken").replaceAll(" ", "+");
          // Change it by PS; only checks (configurations, aio exceptions etc..)
          if (rt.contains(":")) {
              String[] parts = rt.split(":");
              if (parts.length > 2) {
                  cursorMark = parts[0];
                  set = parts[1];
                  JSONObject setsInConfig = Options.getInstance().getJSONObject("OAI").getJSONObject("sets");
                  if (setsInConfig.has(set)) {
                      query.addFilterQuery(Options.getInstance().getJSONObject("OAI").getJSONObject("sets")
                              .getJSONObject(set).getString("filter"));
                  }
                  if (parts.length>=3) {
                      format=OAIMetadataFormat.valueOf(parts[2]);
                  }
              }
          }
          query.setParam(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
      } else {
          query.setParam(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
      }


      
      QueryResponse qr = getClient().query(DataCollections.catalog.name(), query);
      String nextCursorMark = qr.getNextCursorMark();
      SolrDocumentList docs = qr.getResults();
      if (docs.getNumFound() == 0) {
        ret.append("<error code=\"noRecordsMatch\">no record match the search criteria</error>");
      } else {
        ret.append("<" + verb + ">");

        for (SolrDocument doc : docs) {

          MarcRecord mr = MarcRecord.fromDocDep(doc);
          Date datestamp = (Date) doc.getFirstValue(SORT_FIELD);
          boolean deletedStatus = mr.dntstav != null && mr.dntstav.size() > 0 && mr.dntstav.get(0).equals(PublicItemState.D.name());
          ret.append("<record>");
          if (deletedStatus) {
              ret.append("<header status=\"deleted\">");
          } else {
              ret.append("<header>");
          }
          Object id = oaiIdentifier(doc);
          ret.append("<identifier>").append(id).append("</identifier>");
          ret.append("<datestamp>")
                  .append(DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault()).format(datestamp.toInstant()))
                  .append("</datestamp>");
          ret.append("<setSpec>").append(set).append("</setSpec>");
          ret.append("</header>");

          // Changed identifiers
          if (!deletedStatus) {
              if (!onlyIdentifiers) {
                  ret.append(format.record(mr));
              }
              //ret.append(mr.toXml(onlyIdentifiers, false));
          }
          ret.append("</record>");
        }
        solr.close();
        if (docs.size() == rows) {
          Date last = (Date) docs.get(docs.size() - 1).getFieldValue(SORT_FIELD);

          ret.append("<resumptionToken completeListSize=\"" + docs.getNumFound() + "\">")
                  //.append(DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSS").withZone(ZoneId.systemDefault()).format(last.toInstant()))
                  .append(nextCursorMark).append(":").append(set).append(":").append(format.name())
                  .append("</resumptionToken>");
        }
        ret.append("</" + verb + ">");
      }
      ret.append("</OAI-PMH>");
      return ret.toString();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return ex.toString();
    }
  }

private static Object oaiIdentifier(SolrDocument doc) {
    Object id = doc.getFirstValue("identifier");
      // sdnnt identifier
      Object idSdnnt = doc.getFirstValue("id_sdnnt");
      if (idSdnnt != null) {
          id  = makeSDNNTIdentifier(idSdnnt);
      }
    return id;
}

  private static Object makeSDNNTIdentifier(Object idSdnnt) {
      return String.format("oai:%s:%s", sdnntPrefixIdentifier(), idSdnnt);
  }

  private static String sdnntPrefixIdentifier() {
      JSONObject oaiJSON = Options.getInstance().getJSONObject("OAI");
      return oaiJSON.optString("domain", SDNNT_PREFIX_IDENTIFIER);
  }
  
public static String getRecord(HttpServletRequest req) {

    Options opts = Options.getInstance();
    StringBuilder ret = new StringBuilder();
    ret.append(headerOAI())
            .append(responseDateTag())
            .append(requestTag(req));
    try (SolrClient solr = new HttpSolrClient.Builder(opts.getString("solr.host")).build()) {
        String id = req.getParameter("identifier");
        if (id.contains(sdnntPrefixIdentifier())) {
            int index = id.indexOf(sdnntPrefixIdentifier());
            id = id.substring(index + (sdnntPrefixIdentifier()+":").length());
        }
        
      SolrQuery query = new SolrQuery("*")
              .setRows(1)
              .addFilterQuery("identifier:\"" + id + "\" OR id_sdnnt:\""+id+"\"")
              .setFields(SORT_FIELD, "identifier,id_sdnnt,raw,dntstav,datum_stavu,license,license_history,historie_stavu,granularity, masterlinks, masterlinks_disabled");

      /** to je blbe */
      String set = req.getParameter("set");
      if ("SDNNT-A".equals(set)) {
        query.addFilterQuery("dntstav:A");
      } else if ("SDNNT-N".equals(set)) {
        query.addFilterQuery("dntstav:N");
      } else {
        query.addFilterQuery("dntstav:*");
      }
      
      OAIMetadataFormat format = OAIMetadataFormat.marc21;
      if (StringUtils.isAnyString(req.getParameter(METADATA_PREFIX_PARAMETER))) {
          format=OAIMetadataFormat.valueOf(req.getParameter(METADATA_PREFIX_PARAMETER));
      }
      
      

      SolrDocumentList docs = solr.query(DataCollections.catalog.name(), query).getResults();
      if (docs.getNumFound() == 0) {
        ret.append("<error code=\"idDoesNotExist\">No matching identifier</error>");
      } else {
        ret.append("<GetRecord>");
        for (SolrDocument doc : docs) {

          Date datestamp = (Date) doc.getFirstValue(SORT_FIELD);

          MarcRecord mr = MarcRecord.fromDocDep(doc);

          boolean deletedStatus = mr.dntstav != null && mr.dntstav.get(0).equals(PublicItemState.D.name());
          ret.append("<record>");
          if (deletedStatus) {
              ret.append("<header status=\"deleted\">");
          } else {
              ret.append("<header>");
          }

          ret.append("<identifier>").append(oaiIdentifier(doc)).append("</identifier>");
          ret.append("<datestamp>")
                  .append(DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault()).format(datestamp.toInstant()))
                  .append("</datestamp>");
          if (set != null) {
              ret.append("<setSpec>").append(set).append("</setSpec>");
          }
          ret.append("</header>");
          if (!deletedStatus) {
              ret.append(format.record(mr));
              //ret.append(mr.toXml(false, false));
          }
          ret.append("</record>");
        }
        ret.append("</GetRecord>");
      }
      ret.append("</OAI-PMH>");
      solr.close();

      return ret.toString();
    } catch (SolrServerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return ex.toString();
    }
  }

}
