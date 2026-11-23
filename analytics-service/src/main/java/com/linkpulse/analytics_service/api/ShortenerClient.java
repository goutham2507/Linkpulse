package com.linkpulse.analytics_service.api;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks shortener-service who the caller is and which links they own.
 * The caller's own token is forwarded, so the shortener does all token checking and no JWT secret is shared.
 */
@Component
public class ShortenerClient {

	private static final int PAGE_SIZE = 50; // WHY: the shortener caps page size at 50
	private static final int MAX_PAGES = 20; // WHY: bounds the summary to 1000 links so one user can't cause unbounded calls

	private final RestClient rest;

	public ShortenerClient(ObjectProvider<RestClient.Builder> builder,
			@Value("${app.shortener.base-url}") String baseUrl) {
		// WHY: short timeouts so a slow shortener fails the stats call fast instead of hanging a request thread
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
		factory.setReadTimeout(Duration.ofSeconds(2));
		this.rest = builder.getIfAvailable(RestClient::builder).baseUrl(baseUrl).requestFactory(factory).build();
	}

	/** Returns normally only if the token's user owns {@code code}. */
	public void requireOwner(String code, String authorization) {
		requireToken(authorization);
		try {
			rest.get().uri("/v1/links/{code}", code)
					.header(HttpHeaders.AUTHORIZATION, authorization)
					.retrieve().toBodilessEntity();
		} catch (HttpStatusCodeException e) {
			throw translate(e);
		} catch (RestClientException e) {
			throw unavailable();
		}
	}

	/** Codes of every link the token's user owns (up to 1000). */
	public List<String> myCodes(String authorization) {
		requireToken(authorization);
		List<String> codes = new ArrayList<>();
		try {
			for (int page = 0; page < MAX_PAGES; page++) {
				LinkPage result = rest.get()
						.uri("/v1/links?page={p}&size={s}", page, PAGE_SIZE)
						.header(HttpHeaders.AUTHORIZATION, authorization)
						.retrieve().body(LinkPage.class);
				if (result == null || result.content() == null) {
					break;
				}
				result.content().forEach(l -> codes.add(l.code()));
				if (page + 1 >= result.totalPages()) {
					break;
				}
			}
		} catch (HttpStatusCodeException e) {
			throw translate(e);
		} catch (RestClientException e) {
			throw unavailable();
		}
		return codes;
	}

	private static void requireToken(String authorization) {
		if (authorization == null || authorization.isBlank()) {
			throw new StatsApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
	}

	private static StatsApiException translate(HttpStatusCodeException e) {
		HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
		if (status == HttpStatus.UNAUTHORIZED) {
			return new StatsApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
		// WHY: 403 and 404 both answer "not found", so a user can't probe which codes belong to other people
		if (status == HttpStatus.FORBIDDEN || status == HttpStatus.NOT_FOUND) {
			return new StatsApiException(HttpStatus.NOT_FOUND, "Link not found");
		}
		return unavailable();
	}

	private static StatsApiException unavailable() {
		return new StatsApiException(HttpStatus.SERVICE_UNAVAILABLE, "Link service is unavailable. Try again shortly.");
	}

	private record LinkItem(String code) {
	}

	private record LinkPage(List<LinkItem> content, int totalPages) {
	}
}
