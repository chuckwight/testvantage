package org.testvantage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@WebServlet("/test")
public class Selector extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static String deployment_id = "1";
	private static String client_id = "testvantage34495";
	private static String q = null;
	private String tool_url = null;
	private String tool_state = null;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/html;charset=UTF-8");
		PrintWriter out = response.getWriter();	 
		StringBuffer buf = new StringBuffer();

		String userRequest = request.getParameter("UserRequest");
		if (userRequest == null) userRequest = "";

		try {
			switch (userRequest) {
			default:
				tool_url = request.getParameter("ToolURL");
				if (tool_url == null) { // show form to select ChemVantage production or development server
					buf.append("<h2>Select a ChemVantage server to test</h2>"
							+ "<form method=get>"
							+ "<label><input type=radio name=ToolURL value='https://www.chemvantage.org' />www.chemvantage.org</label><br/>"
							+ "<label><input type=radio name=ToolURL value='https://dev-vantage-hrd.appspot.com' />dev-vantage-hrd.appspot.com</label><br/><br/>"
							+ "<input type=submit>"
							+ "</form>");
				} else {
					buf.append("<h2>Running tests against " + tool_url + "</h2>");
					buf.append(getOIDCToken());
					buf.append("<h3>Select a test launch:</h3>"
							+ "<form method=post><input type=submit name=UserRequest value='Launch Quiz1' /></form><br/><br/>");
				}
			}
		} catch (Exception e) {
			buf.append("<h2>Error</h2>" + e.toString() + ": " + e.getMessage());
		}

		out.println(buf.toString());
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/html");
		PrintWriter out = response.getWriter();	 
		try {
		switch (request.getParameter("UserRequest")) {
		case "Launch Quiz1":
			out.println(launchLtiResourceLink());
			break;
		}
		} catch (Exception e) {
			out.println("Error: " + e.toString() + " " + e.getMessage());
		}
	}

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
	
	private String getParameter(String p, String q) {
		int i = q.indexOf(p + "=") + p.length() + 1;
		int j = q.indexOf("&",i);
		if (j == -1) j = q.length();
		return q.substring(i,j);
	}
	
	protected String launchLtiResourceLink() throws Exception {
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
		payload.addProperty("sub",getParameter("login_hint",q));
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

		buf.append("<form id=autoSubmitForm method=post action='" + tool_url + "/lti/launch'>"
				+ "<input type=hidden name=id_token value='" + id_token + "' />"
				+ "<input type=hidden name=state value='" + tool_state + "' />"
				//+ "<input type=submit value='Launch the assignment now' />"
				+ "</form>"
				+ "<script>"
				+ "window.onload=document.getElementById('autoSubmitForm').submit();"
				+ "</script>");
		return buf.toString();
	}
}
