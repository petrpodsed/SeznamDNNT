/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.inovatika.sdnnt.sched;

import cz.inovatika.sdnnt.InitServlet;
import cz.inovatika.sdnnt.Options;
import cz.inovatika.sdnnt.index.Indexer;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import cz.inovatika.sdnnt.indexer.models.NotificationInterval;
import cz.inovatika.sdnnt.services.*;
import cz.inovatika.sdnnt.services.exceptions.AccountException;
import cz.inovatika.sdnnt.services.exceptions.ConflictException;
import cz.inovatika.sdnnt.services.exceptions.NotificationsException;
import cz.inovatika.sdnnt.services.exceptions.UserControlerException;
import cz.inovatika.sdnnt.services.impl.*;
import cz.inovatika.sdnnt.services.impl.hackcerts.HttpsTrustManager;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.InterruptableJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;

/**
 *
 * @author alberto
 */
public class Job implements InterruptableJob {

  private static final Logger LOGGER = Logger.getLogger(Job.class.getName());


  public Job() {
   
  }

  public void fire(String jobName) {
    LOGGER.log(Level.INFO, "Job {0} fired", jobName);
    fire(Options.getInstance().getJSONObject("jobs").getJSONObject(jobName));
  }

  public void fire(JSONObject jobData) {
    jobData.put("interrupted", false);
    String action = jobData.getString("type");  
    Actions actionToDo = Actions.valueOf(action.toUpperCase());
    actionToDo.doPerform(jobData);
  }

  @Override
  public void interrupt() throws UnableToInterruptJobException {
      //
  }

  @Override
  public void execute(JobExecutionContext jec) throws JobExecutionException {
    String jobName = "unnamed"; 
    try {
      JobDataMap data = jec.getMergedJobDataMap();
      jobName = (String) data.get("jobname");
      String jobKey = jec.getJobDetail().getKey().toString();
      String path = InitServlet.CONFIG_DIR + File.separator + "sched.state";
      String state = "";
      File f = new File(path);
      if (f.exists() && f.canRead()) {
        state = FileUtils.readFileToString(f, "UTF-8");
      }
      if ("paused".equals(state) || SchedulerMgr.getInstance().isPaused()) {
        LOGGER.log(Level.INFO, "Scheduler is paused. Nothing to do.", jobKey);
        return;
      }
      int i = 0;
      for (JobExecutionContext j : jec.getScheduler().getCurrentlyExecutingJobs()) {
        if (jobKey.equals(j.getJobDetail().getKey().toString())) {
          i++;
        }
      }
      if (i > 1) {
        LOGGER.log(Level.INFO, "jobKey {0} is still running. Nothing to do.", jobKey);
        return;
      }

      fire(jobName);
      LOGGER.log(Level.FINE, "job {0} finished", jobKey);
    } catch (SchedulerException | IOException ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      LOGGER.log(Level.SEVERE, "Can't execute job " + jobName, ex);
    }
  }
  
  enum Actions {

    PX_CHECK {
      @Override
      void doPerform(JSONObject jobData) {
        try {
          String range = jobData.optString("range");
          HttpsTrustManager.allowAllSSL();
          PXService service = new PXServiceImpl(range);
          List<String> check = service.check();
          if (!check.isEmpty()) {
            // maximum is one third of total maximum
            JSONObject checkKamerius = Options.getInstance().getJSONObject("check_kramerius");
            int maximum = 100;
            if (checkKamerius.has("itemsInRequest")) {
              maximum = checkKamerius.getInt("itemsInRequest");
            }
            int numberOfBatch = check.size() / maximum;
            if (check.size() % maximum >0) {
              numberOfBatch = numberOfBatch +1;
            }
            for (int i = 0; i < numberOfBatch; i++) {

              int startIndex = i*maximum;
              int endIndex = Math.min((i+1)*maximum, check.size());
              List<String> subList = check.subList(startIndex, endIndex);
              service.request(subList);
            }
          }
        } catch (ConflictException | AccountException | IOException | SolrServerException e) {
          LOGGER.log(Level.SEVERE, e.getMessage(),e);
        }
      }
    },

    WORKFLOW {
      @Override
      void doPerform(JSONObject jobData) {
        try {
            AccountServiceImpl accountService = new AccountServiceImpl( null, null);
            accountService.schedulerSwitchStates();
        } catch (ConflictException | AccountException | IOException | SolrServerException e) {
          LOGGER.log(Level.SEVERE, e.getMessage(),e);
        }
      }
    },

    // Posila email o zmenu stavu zaznamu podle nastaveni uzivatelu
    NOTIFICATIONS {
      @Override
      void doPerform(JSONObject jobData) {
        try {
            MailService mailService = new MailServiceImpl();
            UserControler controler = new UserControlerImpl(/* no reqest */ null);
            NotificationsService notificationsService = new NotificationServiceImpl(controler, mailService);
            notificationsService.processNotifications( NotificationInterval.valueOf(jobData.getString("interval")));
        } catch (UserControlerException | NotificationsException e) {
          LOGGER.log(Level.SEVERE, e.getMessage(),e);
        }
      }
    };
    abstract void doPerform(JSONObject jobData);
  }
  
}
