<%-- 
    Document   : index
    Created on : Jul 29, 2016, 4:06:57 PM
    Author     : lobzik
--%>

<%@page import="org.lobzik.home_sapiens.pi.event.Event"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@ page import="java.util.*"%>
<%@ page import="org.lobzik.home_sapiens.pi.*"%>
<%@ page import="org.lobzik.home_sapiens.entity.*"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
        <h1>Hello World!</h1>
        
<% 
    
if (request.getMethod().equalsIgnoreCase("POST")) {
    String command = request.getParameter("command");
    if (command != null && command.length() > 0) {
        HashMap data = new HashMap();
        data.put("uart_command", command);
        Event e  = new Event ("internal_uart_command", data, Event.Type.USER_ACTION);
        AppData.eventManager.newEvent(e);
    }
}
ParametersStorage ps = AppData.parametersStorage;
MeasurementsCache mc = AppData.measurementsCache;

for (Integer pId: ps.getParameterIds()) {
    Parameter p = ps.getParameter(pId);
    if (mc.getLastMeasurement(p) == null) continue;
%>
        <%=p.getName()%>: <%=mc.getLastMeasurement(p).toStringValue()%> <%=p.getUnit()%><br>
<%}%>
<br> <br>

<b>Internal UART command: </b>
<form action="" method="post">
    <input type="text" name="command" /><input type="submit" value="OK" name="submit" />
</form>
    </body>
</html>
