/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.lobzik.home_sapiens.pi.modules;

import java.math.BigInteger;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.lobzik.home_sapiens.entity.Measurement;
import org.lobzik.home_sapiens.entity.Parameter;
import org.lobzik.home_sapiens.pi.AppData;
import org.lobzik.home_sapiens.pi.BoxCommonData;
import org.lobzik.home_sapiens.pi.BoxSettingsAPI;
import org.lobzik.home_sapiens.pi.ConnJDBCAppender;
import org.lobzik.home_sapiens.pi.event.Event;
import org.lobzik.home_sapiens.pi.event.EventManager;
import static org.lobzik.home_sapiens.pi.modules.ModemModule.test;
import org.lobzik.tools.Tools;
import org.lobzik.tools.db.mysql.DBSelect;
import org.lobzik.tools.db.mysql.DBTools;

/**
 * Implement internal logics of events and data handling
 *
 * @author lobzik
 */
public class InstinctsModule implements Module {

    public final String MODULE_NAME = this.getClass().getSimpleName();
    private static Logger log = null;
    private static InstinctsModule instance = null;

    private InstinctsModule() { //singleton
    }

    public static InstinctsModule getInstance() {

        if (instance == null) {
            instance = new InstinctsModule(); //lazy init
            log = Logger.getLogger(instance.MODULE_NAME);
            if (!test) {
                Appender appender = ConnJDBCAppender.getAppenderInstance(AppData.dataSource, instance.MODULE_NAME);
                log.addAppender(appender);
            }
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
            EventManager.subscribeForEventType(this, Event.Type.SYSTEM_EVENT);
            EventManager.subscribeForEventType(this, Event.Type.SYSTEM_MODE_CHANGED);
            EventManager.subscribeForEventType(this, Event.Type.PARAMETER_UPDATED);
            EventManager.subscribeForEventType(this, Event.Type.PARAMETER_CHANGED);
            EventManager.subscribeForEventType(this, Event.Type.USER_ACTION);

            Parameter p = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("SOCKET"));
            Measurement off = new Measurement(p, false);
            HashMap eventData = new HashMap();
            eventData.put("parameter", p);
            eventData.put("measurement", off);
            Event newE = new Event("init", eventData, Event.Type.PARAMETER_UPDATED);
            AppData.eventManager.newEvent(newE);

            p = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("LAMP_1"));
            off = new Measurement(p, true);
            eventData = new HashMap();
            eventData.put("parameter", p);
            eventData.put("measurement", off);
            newE = new Event("init", eventData, Event.Type.PARAMETER_UPDATED);
            AppData.eventManager.newEvent(newE);

            p = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("LAMP_2"));
            off = new Measurement(p, true);
            eventData = new HashMap();
            eventData.put("parameter", p);
            eventData.put("measurement", off);
            newE = new Event("init", eventData, Event.Type.PARAMETER_UPDATED);
            AppData.eventManager.newEvent(newE);

            p = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("DOOR_SENSOR"));
            off = new Measurement(p, false);
            eventData = new HashMap();
            eventData.put("parameter", p);
            eventData.put("measurement", off);
            newE = new Event("init", eventData, Event.Type.PARAMETER_UPDATED);
            AppData.eventManager.newEvent(newE);

            p = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("WET_SENSOR"));
            off = new Measurement(p, false);
            eventData = new HashMap();
            eventData.put("parameter", p);
            eventData.put("measurement", off);
            newE = new Event("init", eventData, Event.Type.PARAMETER_UPDATED);
            AppData.eventManager.newEvent(newE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent(Event e) {
        switch (e.type) {
            case SYSTEM_EVENT:
                if (e.name.equals("cellid_detected")) {
                    searchForLocationByCellId(e.data);
                }
                break;

            case PARAMETER_CHANGED:
                switch (e.name) {

                    case "user_command": //реакция на команды, для управления 433 через параметры
                    case "script_command":
                        Measurement m = (Measurement) e.data.get("measurement");

                        Parameter p = (Parameter) e.data.get("parameter");
                        String alias = p.getAlias();
                        String uartCommand = "";
                        switch (alias) {
                            case "SOCKET":
                                if (m.getBooleanValue()) {
                                    uartCommand = BoxSettingsAPI.get("Socket1OnCommand433");
                                    log.info("ALIAS:" + alias + ": Включена розетка");

                                } else {
                                    uartCommand = BoxSettingsAPI.get("Socket1OffCommand433");
                                    log.info("ALIAS:" + alias + ": Выключена розетка");
                                }
                                break;

                            case "LAMP_1":
                                if (m.getBooleanValue()) {
                                    uartCommand = BoxSettingsAPI.get("Lamp1OnCommand433");
                                    log.info("ALIAS:" + alias + ": Включена лампа 1");


                                } else {
                                    uartCommand = BoxSettingsAPI.get("Lamp1OffCommand433");
                                    log.info("ALIAS:" + alias + ": Выключена лампа 1");
                                }
                                break;

                            case "LAMP_2":
                                if (m.getBooleanValue()) {
                                    uartCommand = BoxSettingsAPI.get("Lamp2OnCommand433");
                                    log.info("ALIAS:" + alias + ": Включена лампа 2");
                                } else {
                                    uartCommand = BoxSettingsAPI.get("Lamp2OffCommand433");
                                    log.info("ALIAS:" + alias + ": Выключена лампа 2");
                                }
                                break;

                        }
                        if (uartCommand != null && uartCommand.length() > 0) {
                            HashMap data = new HashMap();
                            data.put("uart_command", "433_TX=" + uartCommand);
                            Event reaction = new Event("internal_uart_command", data, Event.Type.USER_ACTION);
                            AppData.eventManager.newEvent(reaction);
                        }
                        break;

                }
                break;

            case USER_ACTION:
                if (e.name.equals("user_command")) {
                    Map commandData = e.data;
                    if (commandData != null) {
                        for (String parName : (Set<String>) commandData.keySet()) {
                            String val = (String) commandData.get(parName);
                            int paramId = AppData.parametersStorage.resolveAlias(parName);

                            if (paramId > 0) {
                                HashMap eventData = new HashMap();
                                Parameter p = AppData.parametersStorage.getParameter(paramId);
                                Measurement m = null;
                                switch (p.getType()) {
                                    case BOOLEAN:
                                        m = new Measurement(p, Tools.parseBoolean(val, null));
                                        break;
                                    //TODO other types?
                                    //да, это единственный случай, когда юзер меняет параметр. просто это очень удобно и лучше я не придумал
                                }
                                eventData.put("parameter", p);
                                eventData.put("measurement", m);
                                Event newE = new Event("user_command", eventData, Event.Type.PARAMETER_UPDATED);

                                AppData.eventManager.newEvent(newE);

                            }
                        }
                    }
                }
                break;

            case PARAMETER_UPDATED:
                Parameter p = (Parameter) e.data.get("parameter");
                if (p != null) {
                    Measurement m = (Measurement) e.data.get("measurement");
                    String alias = p.getAlias();
                    switch (alias) {
                        case "VBAT_SENSOR":
                            int id = AppData.parametersStorage.resolveAlias("CHARGE_ENABLED");
                            Parameter charger = AppData.parametersStorage.getParameter(id);
                            double vbatSumm = 0;
                            int counts = 0;
                            List<Measurement> battHistory = AppData.measurementsCache.getHistory(p);
                            List<Measurement> chargerHistory = AppData.measurementsCache.getHistory(charger);
                            for (Measurement vbatMeasure : battHistory) {
                                if (vbatMeasure.getTime() < System.currentTimeMillis() - 105000) { //105 sec avg
                                    continue;//TOO OLD ;(
                                }
                                //if (AppData.measurementsCache.getLastMeasurement(charge) != null && AppData.measurementsCache.getLastMeasurement(charge).getBooleanValue())
                                //search for charger mesaurement
                                for (Measurement chargerMeasure : chargerHistory) {
                                    if (Math.abs(chargerMeasure.getTime() - vbatMeasure.getTime()) < 1000) {
                                        //this is nearest 
                                        counts++;
                                        if (chargerMeasure.getBooleanValue()) {
                                            vbatSumm += vbatMeasure.getDoubleValue() - 1.85;
                                        } else {
                                            vbatSumm += vbatMeasure.getDoubleValue();
                                        }
                                    }
                                }
                            }

                            double avgBattVoltage = vbatSumm / counts;

                            int chargePercents = 5;
                            if (avgBattVoltage > 5.8) {
                                chargePercents += (avgBattVoltage - 5.8) * 95; //при 6.8 В будет 100%
                            }
                            if (chargePercents > 100) {
                                chargePercents = 100;
                            }
                            Parameter chargeP = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("BATT_CHARGE"));
                            Measurement chargeM = new Measurement(chargeP, chargePercents);
                            HashMap eventData = new HashMap();
                            eventData.put("parameter", chargeP);
                            eventData.put("measurement", chargeM);
                            Event newE = new Event("calculated", eventData, Event.Type.PARAMETER_UPDATED);

                            AppData.eventManager.newEvent(newE);
                            Parameter VACp = AppData.parametersStorage.getParameter(AppData.parametersStorage.resolveAlias("VAC_SENSOR"));
                            
                            if (counts > 5 && VACp.getState() != Parameter.State.OK && AppData.measurementsCache.getAvgMeasurementFrom(p, System.currentTimeMillis() - 300000).getDoubleValue() < 5.8) {
                                String message = "Заряд аккумуляторов критически низок!";
                                log.fatal(message);
                                HashMap cause = new HashMap();
                                cause.put("cause", message);
                                Event shutdown = new Event("shutdown", cause, Event.Type.SYSTEM_EVENT);
                                AppData.eventManager.newEvent(shutdown);
                            }
                            break;

                    }
                    break;
                }
        }
    }

    public static void finish() {

    }

    private void searchForLocationByCellId(Map cellIdData) {
        try {
            String lac = (String) cellIdData.get("LAC");
            String cid = (String) cellIdData.get("CID");
            BigInteger cell = new BigInteger(cid, 16);
            BigInteger area = new BigInteger(lac, 16);
            String sSQL = "select lat, lon from opencellid.megafon_ru where mcc=250 and net=2 and cell=" + cell.toString() + " and area=" + area.toString();
            try (Connection conn = DBTools.openConnection(BoxCommonData.dataSourceName)) {
                List<HashMap> resList = DBSelect.getRows(sSQL, conn);
                if (resList.isEmpty()) {
                    throw new Exception("CellId " + cid + " with lac " + lac + " не найден в базе данных");
                }
                double latitude = Tools.parseDouble(resList.get(0).get("lat"), 0);
                double longitude = Tools.parseDouble(resList.get(0).get("lon"), 0);
                log.info("Определено местоположение!" + latitude + ", " + longitude);
                HashMap eventData = new HashMap();
                eventData.put("latitude", latitude);
                eventData.put("longitude", longitude);
                Event event = new Event("location_detected", eventData, Event.Type.SYSTEM_EVENT);
                AppData.eventManager.newEvent(event);
            }

        } catch (Exception e) {
            log.error("Не удалось определить местоположение: " + e.getMessage());
        }
    }

}
