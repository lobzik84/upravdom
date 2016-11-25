/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.lobzik.home_sapiens.pi.modules;

import java.io.File;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.lobzik.home_sapiens.pi.AppData;
import org.lobzik.home_sapiens.pi.ConnJDBCAppender;
import org.lobzik.home_sapiens.pi.event.Event;
import org.lobzik.home_sapiens.pi.event.EventManager;
import org.lobzik.tools.StreamGobbler;

/**
 *
 * @author lobzik
 */
public class SpeakerModule implements Module {

    public final String MODULE_NAME = this.getClass().getSimpleName();
    private static SpeakerModule instance = null;
    private Process process = null;
    private static Logger log = null;
    private static final String PREFIX = "/usr/bin/sudo";
    private static final String COMMAND = "/usr/bin/aplay";

    private SpeakerModule() { //singleton
    }

    public static SpeakerModule getInstance() {
        if (instance == null) {
            instance = new SpeakerModule(); //lazy init
            log = Logger.getLogger(instance.MODULE_NAME);
            Appender appender = ConnJDBCAppender.getAppenderInstance(AppData.dataSource, instance.MODULE_NAME);
            log.addAppender(appender);
        }
        return instance;
    }

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public void start() {
        try {
            EventManager.subscribeForEventType(this, Event.Type.USER_ACTION);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void play(String file) {
        try {
            String[] env = {"aaa=bbb", "ccc=ddd"};

            String[] args = {PREFIX, COMMAND, AppData.getSoundWorkDir().getAbsolutePath() + File.separator + file};
            File workdir = AppData.getSoundWorkDir();
            Runtime runtime = Runtime.getRuntime();
            long before = System.currentTimeMillis();
            log.debug("Playing " + file + " at " + workdir);
            process = runtime.exec(args, env, workdir);
            StringBuilder output = new StringBuilder();
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), output);
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), output);
            errorGobbler.start();
            outputGobbler.start();
            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                log.error("Error executing, exit status: " + exitValue);
            }
        } catch (Exception e) {
            log.error("Error " + e.getMessage());
        }

    }

    @Override
    public void handleEvent(Event e) {
        if (e.type == Event.Type.USER_ACTION && e.name.equals("play_sound")) {
            play((String) e.data.get("sound_file"));
        }
    }

    public static void finish() {

    }

}
