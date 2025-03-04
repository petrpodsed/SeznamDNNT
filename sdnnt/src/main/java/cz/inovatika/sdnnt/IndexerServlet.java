package cz.inovatika.sdnnt;

import static cz.inovatika.sdnnt.rights.Role.admin;
import static cz.inovatika.sdnnt.rights.Role.kurator;
import static cz.inovatika.sdnnt.rights.Role.mainKurator;
import static cz.inovatika.sdnnt.utils.ServletsSupport.errorJson;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.json.JSONArray;
import org.json.JSONObject;

import cz.inovatika.sdnnt.index.CatalogIterationSupport;
import cz.inovatika.sdnnt.index.DntAlephImporter;
import cz.inovatika.sdnnt.index.Indexer;
import cz.inovatika.sdnnt.index.OAIHarvester;
import cz.inovatika.sdnnt.index.XMLImporterDistri;
import cz.inovatika.sdnnt.index.XMLImporterHeureka;
import cz.inovatika.sdnnt.index.XMLImporterKosmas;
import cz.inovatika.sdnnt.model.User;
import cz.inovatika.sdnnt.rights.RightsResolver;
import cz.inovatika.sdnnt.rights.impl.predicates.MustBeCalledFromLocalhost;
import cz.inovatika.sdnnt.rights.impl.predicates.MustBeLogged;
import cz.inovatika.sdnnt.rights.impl.predicates.UserMustBeInRole;
import cz.inovatika.sdnnt.services.impl.users.UserControlerImpl;
import cz.inovatika.sdnnt.utils.PureHTTPSolrUtils;
import cz.inovatika.sdnnt.utils.QuartzUtils;
import cz.inovatika.sdnnt.utils.ServletsSupport;

/**
 * @author alberto
 */
@WebServlet(value = "/index/*")
public class IndexerServlet extends HttpServlet {

    public static final Logger LOGGER = Logger.getLogger(IndexerServlet.class.getName());
    public static final String ACTION_NAME = "action";

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setDateHeader("Expires", 0); // Proxies.
        PrintWriter out = response.getWriter();
        try {
            String actionNameParam = request.getPathInfo().substring(1);
            if (actionNameParam != null) {
                Actions actionToDo = Actions.valueOf(actionNameParam.toUpperCase());
                JSONObject json = actionToDo.doPerform(request, response);
                out.println(json.toString(2));
            } else {

                out.print("actionNameParam -> " + actionNameParam);
            }
        } catch (IOException e1) {
            LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e1.toString());
            out.print(e1.toString());
        } catch (SecurityException e1) {
            LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (Exception e1) {
            LOGGER.log(Level.SEVERE, e1.getMessage(), e1);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e1.toString());
            out.print(e1.toString());
        }
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    enum Actions {

        
        
        TOUCH {

            private static final int LIMIT = 1000;

            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                long start = System.currentTimeMillis();
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    CatalogIterationSupport support = new CatalogIterationSupport();
                    try {
                        JSONArray jsonArray = new JSONArray();
                        AtomicInteger number = new AtomicInteger(0);
                        Map<String,String> reqMap = new HashMap<>();
                        reqMap.put("rows", ""+LIMIT);

                        List<String> bulk = new ArrayList<>();
                        support.iterate(reqMap, null, new ArrayList<String>(), new ArrayList<String>(), Arrays.asList("identifier"), (rsp) -> {
                            Object identifier = rsp.getFieldValue("identifier");

                            bulk.add(identifier.toString());
                            if (bulk.size() >= LIMIT) {
                                number.addAndGet(bulk.size());
                                LOGGER.info(String.format("Bulk update %d", number.get()));
                                JSONObject returnFromPost = PureHTTPSolrUtils.touchBulk(bulk, "identifier", support.getCollection());
                                jsonArray.put(returnFromPost);
                                bulk.clear();
                            }
                        }, "identifier");
                        
                        if (!bulk.isEmpty()) {
                            number.addAndGet(bulk.size());
                            JSONObject returnFromPost = PureHTTPSolrUtils.touchBulk(bulk, "identifier", support.getCollection());
                            bulk.clear();
                            jsonArray.put(returnFromPost);
                        }

                        JSONObject object = new JSONObject();
                        object.put("numberOfObjects", number.get());
                        object.put("bulkResults", jsonArray);
                        return object;
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.toString());
                    } finally {
                        PureHTTPSolrUtils.commit(support.getCollection());
                        QuartzUtils.printDuration(LOGGER, start);
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }

       },

        // pro interni ucely
        REINDEX_ID {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        return new JSONObject().put("indexed", Indexer.reindexId(req.getParameter("id")));
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.toString());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // pro interni ucely
        REINDEX {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        String dest = Options.getInstance().getString("solr.host");
                        String collection = "catalog";
                        if (req.getParameter("dest") != null) {
                            dest = req.getParameter("dest");
                        }
                        if (req.getParameter("collection") != null) {
                            collection = req.getParameter("collection");
                        }
                        json.put("indexed", Indexer.reindex(dest, req.getParameter("filter"), collection, Boolean.parseBoolean(req.getParameter("cleanStav"))));
                        return json;
                    } catch (Exception ex) {
                        return errorJson(response, SC_FORBIDDEN, "not allowed");
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // plny harvest  skc katalogu - api operace - dostupne pouze pro admin
        FULL {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        OAIHarvester oai = new OAIHarvester();
                        String set = "SKC";
                        String core = "catalog";
                        if (req.getParameter("set") != null) {
                            set = req.getParameter("set");
                        }
                        if (req.getParameter("core") != null) {
                            core = req.getParameter("core");
                        }
                        json.put("indexed", oai.full(set, core,
                                Boolean.parseBoolean(req.getParameter("merge")),
                                Boolean.parseBoolean(req.getParameter("keepDNTFields")),
                                Boolean.parseBoolean(req.getParameter("allFields"))));

                        return json;
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // pokracovani harvestu - posledni zaznam from until
        UPDATE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        OAIHarvester oai = new OAIHarvester();
                        String set = "SKC";
                        String core = "catalog";
                        if (req.getParameter("set") != null) {
                            set = req.getParameter("set");
                        }
                        if (req.getParameter("core") != null) {
                            core = req.getParameter("core");
                        }
                        // Vychozi musime merge
                        boolean merge = true;
                        if (req.getParameter("merge") != null) {
                            merge = Boolean.parseBoolean(req.getParameter("merge"));
                        }
                        json.put("indexed", oai.update(set, core,
                                merge,
                                true,
                                Boolean.parseBoolean(req.getParameter("allFields"))));
                        return json;
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // harvest s datum yyyy-MM-ddTHH:mm:ssZ 2021-01-31T10:19:09Z - posledni zaznam from until
        UPDATE_FROM {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        OAIHarvester oai = new OAIHarvester();
                        String set = "SKC";
                        String core = "catalog";
                        if (req.getParameter("set") != null) {
                            set = req.getParameter("set");
                        }
                        if (req.getParameter("core") != null) {
                            core = req.getParameter("core");
                        }
                        json.put("indexed", oai.updateFrom(set, core,
                                req.getParameter("from"),
                                Boolean.parseBoolean(req.getParameter("merge")),
                                true,
                                Boolean.parseBoolean(req.getParameter("allFields"))));

                        return json;

                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // import info o stavu z Alephu DNT set
        IMPORT_DNTSET {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        DntAlephImporter imp = new DntAlephImporter();
                        return imp.run(req.getParameter("from"));
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // import info o stavu z Alephu DNT set
        RESUME_DNTSET {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    JSONObject json = new JSONObject();
                    try {
                        DntAlephImporter imp = new DntAlephImporter();
                        json = imp.resume(req.getParameter("token"));

                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        json.put("error", ex.toString());
                    }
                    return json;
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // import zaznamu z kosmas - uzivatelske api
        IMPORT_KOSMAS {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        XMLImporterKosmas imp = new XMLImporterKosmas();
                        // https://www.kosmas.cz/atl_shop/nkp.xml
                        return imp.doImport(req.getParameter("url"), req.getParameter("from_id"), Boolean.parseBoolean(req.getParameter("resume")));
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // import zaznamu z distri.cz - uzivatelske api
        IMPORT_DISTRI {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        XMLImporterDistri imp = new XMLImporterDistri();
                        return imp.doImport(req.getParameter("url"), req.getParameter("from_id"), Boolean.parseBoolean(req.getParameter("resume")));
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // import zaznamu z palmknihy.cz - uzivatelske api
        // url: muze byt url nebo cesta file na serveru
        // from_id: pocatecni id pro import
        // resume: pokud true, cte posledni importovani id, a pokracuje
        IMPORT_HEUREKA {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        XMLImporterHeureka imp = new XMLImporterHeureka();
                        // json = imp.fromFile("C:/Users/alberto/Projects/SDNNT/Docs/heureka.xml", "palmknihy", "SHOPITEM");
                        // https://www.palmknihy.cz/heureka.xml

                        return imp.doImport(req.getParameter("url"), req.getParameter("from_id"), Boolean.parseBoolean(req.getParameter("resume")));

                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // pro interni ucely  - porovnani dvou zaznamu
        COMPARE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        JSONObject json = new JSONObject();
                        Indexer indexer = new Indexer();
                        return indexer.compare(req.getParameter("id"));
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        // hleda a najde zaznam podle identifikatoru a vrati marc21
        // api
        FINDID {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    JSONObject ret = new JSONObject();
                    try (SolrClient solr = new HttpSolrClient.Builder(Options.getInstance().getString("solr.host", "http://localhost:8983/solr/")).build()) {

                        SolrQuery q = new SolrQuery("*").setRows(1)
                                .addFilterQuery("identifier:\"" + req.getParameter("id") + "\"")
                                .setFields("raw");

                        SolrDocumentList docs = solr.query("sdnnt", q).getResults();
                        if (docs.getNumFound() > 0) {
                            SolrDocument docDnt = docs.get(0);
                            String jsDnt = (String) docDnt.getFirstValue("raw");
                            SolrDocumentList catDocs = Indexer.find(jsDnt);

                            if (catDocs.getNumFound() == 0) {
                                ret.put("error", "Record not found in catalog");
                                return ret;
                            } else if (catDocs.getNumFound() > 1) {
                                ret.put("error", "Found " + catDocs.getNumFound() + " records in catalog");

                                List<JSONObject> diffs = new ArrayList<>();
                                for (SolrDocument doc : catDocs) {
                                    diffs.add(Indexer.compare(jsDnt, (String) doc.getFirstValue("raw")));
                                }
                                for (int i = 0; i < diffs.size() - 1; i++) {
                                    ret.append("diffs", Indexer.compare(diffs.get(i).toString(), diffs.get(i + 1).toString()));
                                }
                                ret.put("records", diffs);
                            } else {
                                ret.append("docs", catDocs);
                            }
                            return ret;
                        } else {
                            return errorJson("Record not found in sdnnt");
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        MERGEID {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        Indexer indexer = new Indexer();
                        return indexer.mergeId("sdnnt", req.getParameter("id"), "testUser");
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        TEST {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    try {
                        Indexer indexer = new Indexer();
                        return indexer.test("sdnnt", req.getParameter("id"));
                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        MERGECORE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeCalledFromLocalhost()).permit()) {
                    JSONObject json = new JSONObject();
                    try {
                        Indexer indexer = new Indexer();
                        return indexer.mergeCore("sdnnt", "testUser", req.getParameter("from"));
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        },
        SAVE {
            @Override
            JSONObject doPerform(HttpServletRequest req, HttpServletResponse response) throws Exception {
                if (new RightsResolver(req, new MustBeLogged(), new UserMustBeInRole(mainKurator, kurator, admin)).permit()) {
                    User user = new UserControlerImpl(req).getUser();
                    try {
                        Indexer indexer = new Indexer();
                        JSONObject inputJs = ServletsSupport.readInputJSON(req);
                        return indexer.save(req.getParameter("id"), inputJs, user.getUsername());
                    } catch (Exception ex) {
                        return errorJson(response, SC_INTERNAL_SERVER_ERROR, ex.getMessage());
                    }
                } else {
                    return errorJson(response, SC_FORBIDDEN, "not allowed");
                }
            }
        };

        abstract JSONObject doPerform(HttpServletRequest request, HttpServletResponse response) throws Exception;
    }

}
