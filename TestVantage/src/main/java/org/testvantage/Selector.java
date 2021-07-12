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
import com.auth0.jwt.interfaces.DecodedJWT;

@WebServlet("/test")
public class Selector extends HttpServlet {
	private static final long serialVersionUID = 137L;
	private static String deployment_id = "1";
	private static String client_id = "testvantage34495";
	private String tool_url = null;
	private String tool_state = null;
	private DecodedJWT id_token = null;

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

		doGet(request, response);
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
			String q = new URL(redirectUrl).getQuery();
			tool_state = new String(Base64.getUrlDecoder().decode(JWT.decode(getParameter("state",q)).getPayload()));
			Date exp = JWT.decode(getParameter("state",q)).getExpiresAt();
			//JsonObject state = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(JWT.decode(getParameter("state",q)).getPayload()))).getAsJsonObject();
			//Date exp = new Date(state.get("exp").getAsLong());
			
			buf.append("Success. The server gave a valid response to an OICD auth token request.<br/>"
					+ "User: " + getParameter("login_hint",q) + "<br/>"
					+ "Expires: " + exp.toString() + "<br/>"
					+ "Nonce: " + getParameter("nonce",q) + "<br/><br/>"
					+ "<a href='/test?ToolURL=" + tool_url + "'>Refresh token</a> or <a href='/'>Start over</a>");
					//+ "URL: " + redirectUrl);
		}
		return buf.toString();
	}
	
	private String getParameter(String p, String q) {
		int i = q.indexOf(p + "=") + p.length() + 1;
		int j = q.indexOf("&",i);
		if (j == -1) j = q.length();
		return q.substring(i,j);
	}
}
