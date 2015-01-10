package xdi2.connector.facebook.api;


import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.core.syntax.XDIAddress;

public class FacebookApi {

	private static final Logger log = LoggerFactory.getLogger(FacebookApi.class);

	private String appId;
	private String appSecret;

	public FacebookApi() {

		this.appId = null;
		this.appSecret = null;
	}

	public FacebookApi(String appId, String appSecret) {

		this.appId = appId;
		this.appSecret = appSecret;
	}

	public void init() {

	}

	public void destroy() {

	}

	public String startOAuth(HttpServletRequest request, String redirectUri, XDIAddress userXri) throws IOException {

		String clientId = this.getAppId();
		if (redirectUri == null) redirectUri = uriWithoutQuery(request.getRequestURL().toString());
		String scope = "email user_friends";
		String state = userXri.toString();

		// prepare redirect

		log.debug("Starting OAuth...");

		StringBuffer location = new StringBuffer("https://www.facebook.com/v2.0/dialog/oauth/?");
		location.append("client_id=" + URLEncoder.encode(clientId, "UTF-8"));
		location.append("&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8"));
		location.append("&scope=" + URLEncoder.encode(scope, "UTF-8"));
		location.append("&state=" + URLEncoder.encode(state, "UTF-8"));

		// done

		log.debug("OAuth URI: " + location.toString());
		return location.toString();
	}

	public void checkState(Map<?, ?> parameterMap, XDIAddress userXDIAddress) throws IOException {

		String state = parameterMap.containsKey("state") ? ((String[]) parameterMap.get("state"))[0] : null;
		
		checkState(state, userXDIAddress);
	}
	
	public void checkState(String state, XDIAddress userXDIAddress) throws IOException {

		if (state == null) {

			log.warn("No OAuth state received.");
			return;
		}

		if (! userXDIAddress.toString().equals(state)) throw new IOException("Invalid state: " + state);

		log.debug("State OK");
	}

	public String exchangeCodeForAccessToken(String requestURL, Map<?, ?> parameterMap) throws IOException, HttpException {

		String code = parameterMap.containsKey("code") ? ((String[]) parameterMap.get("code"))[0] : null;

		return exchangeCodeForAccessToken(requestURL, code);
	}
	
	public String exchangeCodeForAccessToken(String requestURL, String code) throws IOException, HttpException {

		String clientId = this.getAppId();
		String clientSecret = this.getAppSecret();
		String redirectUri = uriWithoutQuery(requestURL);

		log.debug("Exchanging Code '" + code + "'");

		// send request

		StringBuffer location = new StringBuffer("https://graph.facebook.com/v2.0/oauth/access_token?");
		location.append("client_id=" + URLEncoder.encode(clientId, "UTF-8"));
		location.append("&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8"));
		location.append("&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8"));
		location.append("&code=" + URLEncoder.encode(code, "UTF-8"));

		HttpClient httpClient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(URI.create(location.toString()));
		HttpResponse httpResponse = httpClient.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();

		// read response

		String accessToken = null;

		String content = EntityUtils.toString(httpEntity);
		Charset charset = ContentType.getOrDefault(httpEntity).getCharset();

		List<NameValuePair> nameValuePairs = URLEncodedUtils.parse(content, charset);
		for (NameValuePair nameValuePair : nameValuePairs) if ("access_token".equals(nameValuePair.getName())) { accessToken = nameValuePair.getValue(); break; }

		EntityUtils.consume(httpEntity);
		httpClient.getConnectionManager().shutdown();

		// done

		log.debug("Access Token: " + accessToken);
		return accessToken;
	}

	public void revokeAccessToken(String accessToken) throws IOException, JSONException {

		if (accessToken == null) throw new NullPointerException();

		log.debug("Revoking Access Token '" + accessToken + "'");

		// send request

		StringBuffer location = new StringBuffer("https://graph.facebook.com/v2.0/me/permissions?");
		location.append("access_token=" + accessToken);

		HttpClient httpClient = new DefaultHttpClient();
		HttpDelete httpDelete = new HttpDelete(URI.create(location.toString()));
		HttpResponse httpResponse = httpClient.execute(httpDelete);
		HttpEntity httpEntity = httpResponse.getEntity();

		// read response

		String content = EntityUtils.toString(httpEntity);

		EntityUtils.consume(httpEntity);
		httpClient.getConnectionManager().shutdown();

		// check for error

		if (! "true".equals(content)) throw new IOException("Error from Facebook Graph API: " + content);

		// done

		log.debug("Access token revoked.");
	}

	public JSONObject retrieveUser(String facebookUserId, String accessToken, String fields) throws IOException, JSONException {

		if (accessToken == null) throw new NullPointerException();

		log.debug("Retrieving user for user ID " + facebookUserId + " and access token '" + accessToken + "'");

		// send request

		StringBuffer location = new StringBuffer("https://graph.facebook.com/v2.0/" + facebookUserId + "?");
		location.append("access_token=" + accessToken);
		if (fields != null) location.append("&fields=" + fields);

		HttpClient httpClient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(URI.create(location.toString()));
		HttpResponse httpResponse = httpClient.execute(httpGet);
		HttpEntity httpEntity = httpResponse.getEntity();

		// read response

		String content = EntityUtils.toString(httpEntity);
		JSONObject user = new JSONObject(content);

		EntityUtils.consume(httpEntity);
		httpClient.getConnectionManager().shutdown();

		// check for error

		if (user.has("error")) throw new IOException("Error from Facebook Graph API: " + user.getJSONObject("error").getString("message"));

		// done

		log.debug("User: " + user);
		return user;
	}

	public JSONObject retrieveUser(String accessToken, String fields) throws IOException, JSONException {

		return this.retrieveUser("me", accessToken, fields);
	}

	public String retrieveUserId(String accessToken) throws IOException, JSONException {

		if (accessToken == null) throw new NullPointerException();

		log.debug("Retrieving User ID for access token '" + accessToken + "'");

		// retrieve the Facebook user ID

		JSONObject user = this.retrieveUser(accessToken, null);
		if (user == null) throw new IOException("No user.");

		return user.getString("id");
	}

	private static String uriWithoutQuery(String url) {

		return url.contains("?") ? url.substring(url.indexOf("?")) : url;
	}

	public String getAppId() {

		return this.appId;
	}

	public void setAppId(String appId) {

		this.appId = appId;
	}

	public String getAppSecret() {

		return this.appSecret;
	}

	public void setAppSecret(String appSecret) {

		this.appSecret = appSecret;
	}
}
