package org.testvantage;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@WebServlet("/test")
public class Selector extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static String deployment_id = "1";
	private static String client_id = "testvantage34495";
	private static String rsa_key_id = null;
	private static String quiz1ResourceLinkId = "01ce684d-63db-4a99-bf64-50e47af1de04";
	private String tool_url = null;
	private String tool_state = null;
	private static Builder id_token_builder = null;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/html");
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
		
		switch (request.getParameter("UserRequest")) {
		case "Launch Quiz1":
			out.println(launchLtiResourceLink(quiz1ResourceLinkId));
			break;
		}
	}

	protected String getOIDCToken() throws Exception {
		StringBuffer buf = new StringBuffer();
		Random rand = new Random();
		Date now = new Date();
		
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
			String q = new URL(redirectUrl).getQuery();
			tool_state = new String(Base64.getUrlDecoder().decode(JWT.decode(getParameter("state",q)).getPayload()));
			Date exp = JWT.decode(getParameter("state",q)).getExpiresAt();
			
			if (rsa_key_id==null) rsa_key_id = KeyStore.getAKeyId("test-vantage");
			//Algorithm algorithm = Algorithm.RSA256(null,KeyStore.getRSAPrivateKey(rsa_key_id));
			
			id_token_builder = JWT.create()
					.withIssuer("https://test-vantage.appspot.com")
					.withAudience(client_id)
					.withSubject(getParameter("login_hint",q))
					.withExpiresAt(new Date(now.getTime() + 5400000L))  // 90 minutes from now
					.withClaim("https://purl.imsglobal.org/spec/lti/claim/deployment_id",deployment_id)
					.withClaim("https://purl.imsglobal.org/spec/lti/claim/version", "1.3.0")
					.withClaim("https://purl.imsglobal.org/spec/lti/claim/message_type", "LtiResourceLinkRequest");
			
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
	
	protected String launchLtiResourceLink(String resourceLinkId) {
		StringBuffer buf = new StringBuffer();
		
		if (id_token_builder == null) return "You must first get a valid OIDC token to launch.";
		
		JsonObject resourceLink = new JsonObject();
		resourceLink.addProperty("id", quiz1ResourceLinkId);
		
		JsonArray roleClaim = new JsonArray();
		roleClaim.add("instructor");
		
		id_token_builder = id_token_builder
				.withClaim("https://purl.imsglobal.org/spec/lti/claim/resource_link", resourceLink.toString())
				.withClaim("https://purl.imsglobal.org/spec/lti/claim/roles", roleClaim.toString());
		
		String id_token = id_token_builder.sign(Algorithm.RSA256(null,KeyStore.getRSAPrivateKey(rsa_key_id)));
		//id_token = new String(Base64.getUrlEncoder().withoutPadding().encode(id_token.getBytes()));
		buf.append(id_token);
		
		buf.append("<form id=autoSubmitForm method=post action=" + tool_url + "/lti/launch>"
				+ "<input type=hidden name=id_token value='" + id_token + "' />"
				+ "<input type=hidden name=state value='" + tool_state + "' />"
				+ "<input type=submit value='Launch the assignment now' />"
				+ "</form>"
				+ "<script>"
				+ "window.onload=document.getElementById('autoSubmitForm').submit();"
				+ "</script>");
		
		return buf.toString();
	}
}
