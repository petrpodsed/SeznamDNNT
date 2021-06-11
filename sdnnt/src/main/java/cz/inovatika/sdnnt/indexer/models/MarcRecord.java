/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.inovatika.sdnnt.indexer.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.inovatika.sdnnt.index.ISBN;
import cz.inovatika.sdnnt.index.MD5;
import cz.inovatika.sdnnt.index.RomanNumber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONObject;

/**
 *
 * @author alberto
 */
public class MarcRecord {

  public static final Logger LOGGER = Logger.getLogger(MarcRecord.class.getName());

  // Header fields
  public String identifier;
  public String datestamp;
  public String setSpec;
  public String leader;

  public boolean isDeleted = false;

  // <marc:controlfield tag="001">000000075</marc:controlfield>
  // <marc:controlfield tag="003">CZ PrDNT</marc:controlfield>
  public Map<String, String> controlFields = new HashMap();

//  <marc:datafield tag="650" ind1="0" ind2="7">
//    <marc:subfield code="a">dějiny</marc:subfield>
//    <marc:subfield code="7">ph114390</marc:subfield>
//    <marc:subfield code="2">czenas</marc:subfield>
//  </marc:datafield>
//  <marc:datafield tag="651" ind1=" " ind2="7">
//    <marc:subfield code="a">Praha (Česko)</marc:subfield>
//    <marc:subfield code="7">ge118011</marc:subfield>
//    <marc:subfield code="2">czenas</marc:subfield>
//  </marc:datafield> 
  public Map<String, List<DataField>> dataFields = new HashMap();
  public SolrInputDocument sdoc = new SolrInputDocument();

  final public static List<String> tagsToIndex = 
          Arrays.asList("015", "020", "022", "035", "040", "100", "130", "240", 
                  "245", "246", "250", "260", "264", 
                  "700", "710", "711", "730",
                  "856", "990", "992", "998", "956", "911", "910");

  public static MarcRecord fromJSON(String json) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    MarcRecord mr = objectMapper.readValue(json, MarcRecord.class);
    return mr;
  }

  public JSONObject toJSON() {
    JSONObject json = new JSONObject();
    json.put("identifier", identifier);
    json.put("datestamp", datestamp);
    json.put("setSpec", setSpec);
    json.put("leader", leader);
    json.put("controlFields", controlFields);

    json.put("dataFields", dataFields);
    return json;
  }

  public SolrInputDocument toSolrDoc() {
    sdoc.setField("identifier", identifier);
    sdoc.setField("datestamp", datestamp);
    sdoc.setField("setSpec", setSpec);
    sdoc.setField("leader", leader);
    sdoc.setField("raw", toJSON().toString());

    // Control fields
    for (String cf : controlFields.keySet()) {
      sdoc.addField("controlfield_" + cf, controlFields.get(cf));
    }
    if (leader != null) {
      sdoc.setField("item_type", leader.substring(7, 8));
    }
    sdoc.setField("title_sort", sdoc.getFieldValue("marc_245a"));
    addRokVydani();
    addDedup();
    addEAN();
    return sdoc;
  }

  public void setStav(String new_stav) {
    if (!dataFields.containsKey("990")) {
      List<DataField> ldf = new ArrayList<>();
      DataField df = new DataField("990", " ", " ");
      SubField sf = new SubField("a", new_stav);
      List<SubField> lsf = new ArrayList<>();
      lsf.add(sf);
      df.subFields.put("a", lsf);
      ldf.add(df);
      dataFields.put("990", ldf);
    } else {
      dataFields.get("990").get(0).subFields.get("a").get(0).value = new_stav;
    }

  }

  public void fillSolrDoc() {
    for (String tag : tagsToIndex) {
      if (dataFields.containsKey(tag)) {
        for (DataField df : dataFields.get(tag)) {
          for (String code : df.getSubFields().keySet()) {
            sdoc.addField("marc_" + tag + code, df.getSubFields().get(code).get(0).getValue());
          }
        }
      }
    }
    //if (sdoc.containsKey("marc_245a")) {

    sdoc.setField("title_sort", sdoc.getFieldValue("marc_245a"));
    //}

    addDedup();
    addEAN();
  }

  private void addRokVydani() {
    if (sdoc.containsKey("marc_260c")) {
      for (Object s : sdoc.getFieldValues("marc_260c")) {
        String val = (String) s;
        val = val.replaceAll("\\[", "").replaceAll("\\]", "").trim();
        try {
          // je to integer. Pridame
          int r = Math.abs(Integer.parseInt(val));
          //Nechame jen 4
          if ((r+"").length() > 3) {
            String v = (r+"").substring(0, 4);
            sdoc.addField("rokvydani", v);
          }
          
          return;
        } catch (NumberFormatException ex) {

        }
        // casto maji 'c' nebo 'p' na zacatku c2001 
        if (val.startsWith("c") || val.startsWith("p")) {
          val = val.substring(1);
          try {
            // je to integer. Pridame
            int r = Integer.parseInt(val);
            sdoc.addField("rokvydani", r);
            return;
          } catch (NumberFormatException ex) {

          }
        }
        // [před r. 1937]
        if (val.startsWith("před r.")) {
          val = val.substring(7).trim();
          try {
            // je to integer. Pridame
            int r = Integer.parseInt(val);
            sdoc.addField("rokvydani", r);
            return;
          } catch (NumberFormatException ex) {

          }
        }

      }
    }
  }

  private void addEAN() {
    if (sdoc.containsKey("marc_020a")) {
      for (Object s : sdoc.getFieldValues("marc_020a")) {
        sdoc.addField("ean", ((String) s).replaceAll("-", ""));
      }
    }
  }

  public void addDedup() {
    sdoc.setField("dedup_fields", generateMD5());
  }

  public void addVyjadreni() {
    // Podle 	Thomas Butler Hickey
    // https://text.nkp.cz/o-knihovne/odborne-cinnosti/zpracovani-fondu/informativni-materialy/bibliograficky-popis-elektronickych-publikaci-v-siti-knihoven-cr
    // strana 42
    // https://www.nkp.cz/soubory/ostatni/vyda_cm26.pdf
    
    /*
    Pole 130, 240 a 730 pro unifikovaný název a podpole názvových údajů v polích 700, 710 a
711 umožní po doplnění formátu MARC 21 nebo zavedení nového formátu vygenerovat
alespoň částečné údaje o díle a vyjádření.
Nové pole 337 (společně s poli 336 a 338, která jsou součástí Minimálního záznamu)
nahrazuje dosavadní podpole 245 $h.
    
    https://is.muni.cz/th/xvt2x/Studie_FRBR.pdf
    strana 100
    */
   //  sdoc.setField("vyjadreni", generatjeMD5());
  }

  private String generateMD5() {
    try {

      //ISBN
      String pole = (String) sdoc.getFieldValue("marc_020a");
      ISBN val = new ISBN();

      if (pole != null && !pole.equals("")) {
        //pole = pole.toUpperCase().substring(0, Math.min(13, pole.length()));
        if (!"".equals(pole) && val.isValid(pole)) {
          return MD5.generate(new String[]{pole});
        }
      }

      //ISSN
      pole = (String) sdoc.getFieldValue("marc_022a");
      if (pole != null && !pole.equals("")) {
        //pole = pole.toUpperCase().substring(0, Math.min(13, pole.length()));
        if (!"".equals(pole) && val.isValid(pole)) {
          return MD5.generate(new String[]{pole});
        }
      }

      //ccnb
      pole = (String) sdoc.getFieldValue("marc_015a");
      //logger.log(Level.INFO, "ccnb: {0}", pole);
      if (pole != null && !"".equals(pole)) {
        return MD5.generate(new String[]{pole});
      }

      //Check 245n číslo části 
      String f245n = "";
      String f245nraw = (String) sdoc.getFieldValue("marc_245n");
      if (f245nraw != null) {
        RomanNumber rn = new RomanNumber(f245nraw);
        if (rn.isValid()) {
          f245n = Integer.toString(rn.toInt());
        }
      }

      //Pole 250 údaj o vydání (nechat pouze numerické znaky) (jen prvni cislice)
      String f250a = (String) sdoc.getFieldValue("marc_250a");
      if (f250a != null) {
        f250a = onlyLeadNumbers(f250a);
      }

      //Pole 100 autor – osobní jméno (ind1=1 →  prijmeni, jmeno; ind1=0 → jmeno, prijmeni.  
      //Obratit v pripade ind1=1, jinak nechat)
      String f100a = (String) sdoc.getFieldValue("marc_100a");
      if (dataFields.containsKey("100") && f100a != null) {
        String ind1 = dataFields.get("100").get(0).ind1;
        if ("1".equals(ind1) && !"".equals(f100a)) {
          String[] split = f100a.split(",", 2);
          if (split.length == 2) {
            f100a = split[1] + split[0];
          }
        }
      }

      if ("".equals(f100a)) {
        f100a = (String) sdoc.getFieldValue("marc_245c");
      }

      //vyber poli
      String uniqueCode = MD5.generate(new String[]{
        (String) sdoc.getFieldValue("marc_245a"),
        (String) sdoc.getFieldValue("marc_245b"),
        //map.get("245c"),
        f245n,
        (String) sdoc.getFieldValue("marc_245p"),
        f250a,
        f100a,
        (String) sdoc.getFieldValue("marc_110a"),
        (String) sdoc.getFieldValue("marc_111a"),
        (String) sdoc.getFieldValue("marc_260a"),
        (String) sdoc.getFieldValue("marc_260b"),
        onlyLeadNumbers((String) sdoc.getFieldValue("marc_260c")),
        (String) sdoc.getFieldValue("marc_264a"),
        (String) sdoc.getFieldValue("marc_264b"),
        onlyLeadNumbers((String) sdoc.getFieldValue("marc_264c"))
      });
      return uniqueCode;
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      return null;
    }

  }

  private static String onlyLeadNumbers(String s) {
    if (s == null || "".equals(s)) {
      return s;
    }
    String retVal = "";
    int n = 0;
    while (n < s.length() && Character.isDigit(s.charAt(n))) {
      retVal += s.charAt(n);
      n++;
    }
    return retVal;
  }

}
