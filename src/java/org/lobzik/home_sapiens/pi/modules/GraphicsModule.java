/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.lobzik.home_sapiens.pi.modules;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.Date;
import org.apache.log4j.Logger;
import org.lobzik.home_sapiens.pi.AppData;
import org.lobzik.home_sapiens.pi.BoxCommonData;
import org.lobzik.home_sapiens.pi.event.Event;
import org.lobzik.tools.db.mysql.DBTools;

/**
 *
 * @author lobzik
 */
import javax.imageio.ImageIO;
import org.lobzik.home_sapiens.pi.event.EventManager;
import org.lobzik.tools.Tools;
public class GraphicsModule implements Module {

    public final String MODULE_NAME = this.getClass().getSimpleName();
    private static GraphicsModule instance = null;
    private static Connection conn = null;
    private static Process process = null;
    private static PrintWriter pw = null;
    private static boolean run = true;

    private static Logger log = null;
    private static final String PREFIX = "/usr/bin/sudo";
    private static final String COMMAND = "/usr/bin/fbi";
    private GraphicsModule() { //singleton
    }

    public static GraphicsModule getInstance() {
        if (instance == null) {
            instance = new GraphicsModule(); //lazy init
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
        
        try {
            conn = DBTools.openConnection(BoxCommonData.dataSourceName);

        } catch (Exception e) {
            e.printStackTrace();
        }      

        try {
            File file = new File(AppData.getGraphicsWorkDir().getAbsolutePath() + File.separator + "screen.jpg");
            startProcess(file);

        } catch (Exception e) {
            e.printStackTrace();
        } 
                
        
    }
    
    public static void startProcess(File file) {
        try {
            while (run) {

                String[] env = {"aaa=bbb", "ccc=ddd"};
                String[] args = {PREFIX, COMMAND, AppData.getGraphicsWorkDir().getAbsolutePath() + File.separator + file};
                File workdir = AppData.getGraphicsWorkDir();
                Runtime runtime = Runtime.getRuntime();
                //log.debug("Drawing " + txt);
                process = runtime.exec(args, env, workdir);

                GraphicsModule.StreamGobbler errorGobbler = new GraphicsModule.StreamGobbler(process.getErrorStream());
                GraphicsModule.StreamGobbler outputGobbler = new GraphicsModule.StreamGobbler(process.getInputStream());
                errorGobbler.start();
                outputGobbler.start();

                pw = new PrintWriter( process.getOutputStream() );

                process.waitFor();
                }
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }

    @Override
    public void handleEvent(Event e) {
        if (e.type == Event.Type.USER_ACTION && e.name.equals("show_image")) {
            draw((String)e.data.get("image_file"));
        }
        if (e.type == Event.Type.TIMER_EVENT && e.name.equals("internal_sensors_poll")) {
            draw("");
        }
        
    }
    
        private void draw(String txt) {
        try {
            //sudo fbi logo.jpg --noverbose -T 1
            Graphics g= null;
            BufferedImage img = null;
            BufferedImage temperatureImg = null;

            try {
                img = ImageIO.read(new File(AppData.getGraphicsWorkDir().getAbsolutePath() + File.separator + "screen.jpg"));
                temperatureImg = ImageIO.read(new File(AppData.getGraphicsWorkDir().getAbsolutePath() + File.separator + "temperature.png"));
            } catch (IOException e) {
            }
            
            g = img.getGraphics();
            g.setColor(new Color(243, 67, 54));
            g.fillRect(0,251, 480, 320);
            //g.drawLine(20, 20, 360, 20);
            g.setColor(new Color(0, 0, 0));
            g.setFont(new Font("Tahoma",  Font.PLAIN, 110));
            Date d = new Date();

            g.drawString(Tools.getFormatedDate(d,"HH:mm"), 100, 180);

            g.drawImage(temperatureImg, 25, 270, null);  
            
            g.setColor(new Color(255, 255, 255));
            g.setFont(new Font("Tahoma",  Font.PLAIN, 20));
            g.drawString(Tools.getFormatedDate(d,"HH:mm"), 55, 290);
                        
            File file = new File(AppData.getGraphicsWorkDir().getAbsolutePath() + File.separator + "newimage.png");
            ImageIO.write(img, "png", file);

            if(!process.isAlive()){
                startProcess(file);
            }
            else {
                pw.print(" ");
            }
    

        } catch (Exception e) {
            log.error("Error " + e.getMessage());
        }

    }

    public static void finish() {
        run = false;
        DBTools.closeConnection(conn);
        process.destroy();
        int exitValue = process.exitValue();
            //log.debug(StreamGobbler.getAllOutput());
            GraphicsModule.StreamGobbler.clearOutput();
            if (exitValue != 0) {
                log.error("Error executing, exit status: " + exitValue);
            }
    }
    
    
    public static class StreamGobbler extends Thread {

        InputStream is;
        private static StringBuilder output = new StringBuilder();

        public StreamGobbler(InputStream is) {
            this.is = is;
        }

        @Override
        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

        public static String getAllOutput() {
            return output.toString();
        }

        public static void clearOutput() {
            output = new StringBuilder();
        }
    }
}
