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
import org.lobzik.home_sapiens.pi.BoxMode;
import org.lobzik.home_sapiens.pi.BoxSettingsAPI;
import org.lobzik.home_sapiens.pi.ConnJDBCAppender;
import org.lobzik.home_sapiens.pi.MeasurementsCache;
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
public class BehaviorModule implements Module {

    public final String MODULE_NAME = this.getClass().getSimpleName();
    private static Logger log = null;
    private static BehaviorModule instance = null;

    private BehaviorModule() { //singleton
    }

    public static BehaviorModule getInstance() {

        if (instance == null) {
            instance = new BehaviorModule(); //lazy init
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent(Event e) {
        switch (e.type) {
            case SYSTEM_EVENT:
                searchForLocationByCellId(e.data);
                break;

            case SYSTEM_MODE_CHANGED:
                if (BoxMode.isArmed()) {
                    log.warn("Включен режим Охрана");
                } else if (BoxMode.isIdle()) {
                    log.warn("Включен режим Хозяин Дома");
                }

                break;

            case PARAMETER_CHANGED:
                switch (e.name) {
                    case "mic_noise": //just for debug of microphone module, to be removed
                        Measurement m = (Measurement) e.data.get("measurement");
                        HashMap data = new HashMap();
                        if (m.getBooleanValue()) {
                            data.put("uart_command", "led1=on");
                        } else {
                            data.put("uart_command", "led1=off");
                        }
                        Event reaction = new Event("internal_uart_command", data, Event.Type.USER_ACTION);
                        AppData.eventManager.newEvent(reaction);
                        break;

                    case "user_command": //реакция на команды, для управления 433 через параметры
                        m = (Measurement) e.data.get("measurement");

                        Parameter p = (Parameter) e.data.get("parameter");
                        String alias = p.getAlias();
                        String uartCommand = "";
                        switch (alias) {
                            case "SOCKET":
                                if (m.getBooleanValue()) {
                                    uartCommand = BoxSettingsAPI.get("Socket1OnCommand433");
                                } else {
                                    uartCommand = BoxSettingsAPI.get("Socket1OffCommand433");
                                }
                                break;

                            case "LAMP_1":
                                if (m.getBooleanValue()) {
                                    uartCommand = BoxSettingsAPI.get("Lamp1Command433");
                                } else {
                                    uartCommand = BoxSettingsAPI.get("Lamp1Command433");
                                }
                                break;

                            case "LAMP_2":
                                if (m.getBooleanValue()) {
                                    uartCommand = BoxSettingsAPI.get("Lamp2Command433");
                                } else {
                                    uartCommand = BoxSettingsAPI.get("Lamp2Command433");
                                }
                                break;

                        }
                        if (uartCommand != null && uartCommand.length() > 0) {
                            data = new HashMap();
                            data.put("uart_command", "433_TX=" + uartCommand);
                            reaction = new Event("internal_uart_command", data, Event.Type.USER_ACTION);
                            AppData.eventManager.newEvent(reaction);
                        }
                        break;

                    default:
                        p = (Parameter) e.data.get("parameter");
                        if (p != null) {
                            m = (Measurement) e.data.get("measurement");
                            switch (p.getAlias()) {
                                case "INTERNAL_TEMP":
                                    if (m.getDoubleValue() > BoxSettingsAPI.getDouble("InTempAlertMax") || m.getDoubleValue() < BoxSettingsAPI.getDouble("InTempAlertMin")) {
                                        p.setState("Alert");
                                    } else {
                                        p.setState(null);
                                    }
                                    break;
                                case "VAC_SENSOR":
                                    if (m.getDoubleValue() > BoxSettingsAPI.getDouble("VACAlertMax") || m.getDoubleValue() < BoxSettingsAPI.getDouble("VACAlertMin")) {
                                        p.setState("Alert");
                                    } else {
                                        p.setState(null);
                                    }

                                    break;
                                case "VBAT_SENSOR":
                                    double avgBattVoltage = AppData.measurementsCache.getAvgMeasurement(p).getDoubleValue();
                                    int chargePercents = 5;
                                    if (avgBattVoltage > 500) {
                                        chargePercents += (avgBattVoltage - 500) / 5; //при 1000 будет 100%
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
                                    break;

                            }
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
                    throw new Exception("CellId " + cid + " with lac " + lac + " not found in DB");
                }
                double latitude = Tools.parseDouble(resList.get(0).get("lat"), 0);
                double longitude = Tools.parseDouble(resList.get(0).get("lon"), 0);
                log.info("Detected location " + latitude + ", " + longitude);
                HashMap eventData = new HashMap();
                eventData.put("latitude", latitude);
                eventData.put("longitude", longitude);
                Event event = new Event("location_detected", eventData, Event.Type.SYSTEM_EVENT);
                AppData.eventManager.newEvent(event);
            }

        } catch (Exception e) {
            log.error("Error getting locatuion: " + e.getMessage());
        }
    }

}
