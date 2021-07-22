package org.testvantage;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@WebServlet("/test")
public class Selector extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static String deployment_id = "1";
	private static String client_id = "testvantage34495";
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/html;charset=UTF-8");
		PrintWriter out = response.getWriter();	 
		
		String tool_url = request.getParameter("ToolURL");
		if (tool_url == null) throw new ServletException("Tool URL must be specified.");
		tool_url = tool_url.equals("prod")?"https://www.chemvantage.org":"https://dev-vantage-hrd.appspot.com";
		
		String test = request.getParameter("Test");
		if (test == null) throw new ServletException("Test must be specified.");
		
		String user = "user" + new Random().nextInt(1000);
		String state = getAuthToken(tool_url,user);
		if (state == null) throw new ServletException("AuthToken was not granted by the server.");
		
		switch (test) {
		case "AuthToken":
			out.println("Success. AuthToken = " + state);
			return;
		case "Quiz1":
			out.println(ltiResourceLinkLaunch(tool_url,state,user,"01ce684d-63db-4a99-bf64-50e47af1de04"));
			return;
		default:
			out.println(ltiResourceLinkLaunch(tool_url,state,user,"01ce684d-63db-4a99-bf64-50e47af1de04"));
		}		
	}

	protected String ltiResourceLinkLaunch(String tool_url,String state,String user,String resourceLinkId) {
		StringBuffer res = new StringBuffer();
		try {
			Encoder enc = Base64.getUrlEncoder().withoutPadding();
			String rsa_key_id = KeyStore.getAKeyId("test-vantage");
			Date now  = new Date();
			
			JsonObject resourceLink = new JsonObject();
			resourceLink.addProperty("id", resourceLinkId);
			
			JsonArray roleClaim = new JsonArray();
			roleClaim.add("instructor");
			
			// Create a JSON header for the JWT to send as id_token
			JsonObject header = new JsonObject();
			header.addProperty("typ", "JWT");
			header.addProperty("alg", "RS256");
			header.addProperty("kid", rsa_key_id);
			byte[] hdr = enc.encode(header.toString().getBytes("UTF-8"));

			// Create the id_token payload
			JsonObject payload = new JsonObject();
			payload = new JsonObject();
			payload.addProperty("iss", "https://test-vantage.appspot.com");
			payload.addProperty("aud",client_id);
			payload.addProperty("sub",user);
			payload.addProperty("exp",(new Date(now.getTime() + 5400000L).getTime()/1000));
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/deployment_id",deployment_id);
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0");
			payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest");
			
			payload.add("https://purl.imsglobal.org/spec/lti/claim/resource_link",resourceLink);
			payload.add("https://purl.imsglobal.org/spec/lti/claim/roles",roleClaim);
			
			byte[] pld = enc.encode(payload.toString().getBytes("UTF-8"));
			
			// Join the header and payload together with a period separator:
			String id_token = String.format("%s.%s",new String(hdr),new String(pld));

			// Add a signature item to complete the JWT:
			Signature signature = Signature.getInstance("SHA256withRSA");
			
			signature.initSign(KeyStore.getRSAPrivateKey(rsa_key_id),new SecureRandom());
			signature.update(id_token.getBytes("UTF-8"));
			String sig = new String(enc.encode(signature.sign()));
			id_token = String.format("%s.%s", id_token, sig);

			URL url = new URL(tool_url + "/lti/launch");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
			conn.setRequestProperty("charset", "utf-8");
			
			// write the message to the output stream
			DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
			wr.writeBytes("state=" + state + "&id_token=" + id_token);
			wr.flush();
			
			conn.connect();
			
			// read the assignment page
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));				
			res.append("The regression test for " + tool_url + " failed for ResourceLinkId " + resourceLinkId + ":<br/><br/>");
    		String line;
    		while ((line = reader.readLine()) != null) {
    			res.append(line);
    		}
    		reader.close();
    		wr.close();
			
			if (res.indexOf("<html>")>0 && res.indexOf("Quiz Rules")>0) return "Regression test passed OK.";
			else {
				// send an email to the ChemVantage administrator
				Properties props = new Properties();
				Session session = Session.getDefaultInstance(props, null);
				Message msg = new MimeMessage(session);
				msg.setFrom(new InternetAddress("chuck.wight@gmail.com", "TestVantage"));
				msg.addRecipient(Message.RecipientType.TO,new InternetAddress("admin@chemvantage.org", "ChemVantage"));
				msg.setSubject("ChemVantage Regression Test Failure");
				msg.setContent(res,"text/html");
				Transport.send(msg);
			}
		} catch (Exception e) {
			return "Error: " + e.toString() + " " + e.getMessage();
		}
		return "Test passed OK.<br/>";
	}
	
	protected String getAuthToken(String server, String user) {
		String query = "?iss=https://test-vantage.appspot.com&login_hint=" + user + "&target_link_uri=" + server + "/lti/launch"
				+ "&lti_deployment_id=" + deployment_id + "&client_id=" + client_id;
		try {
			URL u = new URL(server + "/auth/token" + query);

			HttpURLConnection uc = (HttpURLConnection) u.openConnection();
			uc.setInstanceFollowRedirects(false);
			uc.connect();
			String redirectUrl = uc.getHeaderField("Location");
			if (redirectUrl == null) throw new Exception("ChemVantage server did not grant auth_token.");
			
			String q = new URL(redirectUrl).getQuery();
			String state = getParameter("state",q);
			
			return state;
		} catch (Exception e) {
			return e.getMessage();
		}
	}
/*
	protected String getOIDCToken() throws Exception {
		StringBuffer buf = new StringBuffer();
		Random rand = new Random();
		
		String query = "?iss=https://test-vantage.appspot.com&login_hint=user" + rand.nextInt(1000) + "&target_link_uri=" + tool_url + "/lti/launch"
				+ "&lti_deployment_id=" + deployment_id + "&client_id=" + client_id;
		URL u = new URL(tool_url + "/auth/token" + query);
		
		HttpURLConnection uc = (HttpURLConnection) u.openConnection();
		uc.setInstanceFollowRedirects(false);
		uc.connect();
		String redirectUrl = uc.getHeaderField("Location");
		
		if (redirectUrl == null) 
			buf.append("Server responded: " + uc.getResponseCode() + " " + uc.getResponseMessage());
		else {
			q = new URL(redirectUrl).getQuery();
			tool_state = getParameter("state",q);
			Date exp = JWT.decode(getParameter("state",q)).getExpiresAt();
			
			buf.append("Success. The server gave a valid response to an OICD auth token request.<br/>"
					+ "User: " + getParameter("login_hint",q) + "<br/>"
					+ "Expires: " + exp.toString() + "<br/>"
					+ "Nonce: " + getParameter("nonce",q) + "<br/><br/>"
					+ "<a href='/test?ToolURL=" + tool_url + "'>Refresh token</a> or <a href='/'>Start over</a>");
		}
		return buf.toString();
	}
*/
	private String getParameter(String p, String q) {
		int i = q.indexOf(p + "=") + p.length() + 1;
		int j = q.indexOf("&",i);
		if (j == -1) j = q.length();
		return q.substring(i,j);
	}
	
	protected String launchLtiResourceLink(String server, String state, String user) throws Exception {
		StringBuffer buf = new StringBuffer();
		Date now = new Date();
		String quiz1ResourceLinkId = "01ce684d-63db-4a99-bf64-50e47af1de04";
		Encoder enc = Base64.getUrlEncoder().withoutPadding();
		String rsa_key_id = KeyStore.getAKeyId("test-vantage");
		
		JsonObject resourceLink = new JsonObject();
		resourceLink.addProperty("id", quiz1ResourceLinkId);
		
		JsonArray roleClaim = new JsonArray();
		roleClaim.add("instructor");
		
		// Create a JSON header for the JWT to send as id_token
		JsonObject header = new JsonObject();
		header.addProperty("typ", "JWT");
		header.addProperty("alg", "RS256");
		header.addProperty("kid", rsa_key_id);
		byte[] hdr = enc.encode(header.toString().getBytes("UTF-8"));

		// Create the id_token payload
		JsonObject payload = new JsonObject();
		payload = new JsonObject();
		payload.addProperty("iss", "https://test-vantage.appspot.com");
		payload.addProperty("aud",client_id);
		payload.addProperty("sub",user);
		payload.addProperty("exp",(new Date(now.getTime() + 5400000L).getTime()/1000));
		payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/deployment_id",deployment_id);
		payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0");
		payload.addProperty("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest");
		
		payload.add("https://purl.imsglobal.org/spec/lti/claim/resource_link",resourceLink);
		payload.add("https://purl.imsglobal.org/spec/lti/claim/roles",roleClaim);
		
		byte[] pld = enc.encode(payload.toString().getBytes("UTF-8"));
		
		// Join the header and payload together with a period separator:
		String id_token = String.format("%s.%s",new String(hdr),new String(pld));

		// Add a signature item to complete the JWT:
		Signature signature = Signature.getInstance("SHA256withRSA");
		
		signature.initSign(KeyStore.getRSAPrivateKey(rsa_key_id),new SecureRandom());
		signature.update(id_token.getBytes("UTF-8"));
		String sig = new String(enc.encode(signature.sign()));
		id_token = String.format("%s.%s", id_token, sig);

		buf.append("POST " + server + "<br/>"
				+ "user: " + user + "<br/>"
				+ "id_token: " + id_token + "<br/>"
				+ "state: " + state);
		buf.append("<form id=autoSubmitForm method=post action='" + server + "/lti/launch'>"
				+ "<input type=hidden name=id_token value='" + id_token + "' />"
				+ "<input type=hidden name=state value='" + state + "' />"
				+ "<input type=submit value=Launch />"
				+ "</form>"
				+ "<script>"
				//+ "window.onload=document.getElementById('autoSubmitForm').submit();"
				+ "</script>");
		return buf.toString();
	}
}
