package com.raspel.cardtracker.domain.expense;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class TcmbCurrencyService {

    private final Map<String, Map<String, BigDecimal>> rateCache = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastLiveFetchTime = null;
    
    // Dinamik fallback haritası - başarılı çekilen kurlar burayı günceller
    private static final Map<String, BigDecimal> DYNAMIC_FALLBACKS = new ConcurrentHashMap<>();
    static {
        DYNAMIC_FALLBACKS.put("USD", new BigDecimal("33.00"));
        DYNAMIC_FALLBACKS.put("EUR", new BigDecimal("36.00"));
        DYNAMIC_FALLBACKS.put("GBP", new BigDecimal("42.00"));
        DYNAMIC_FALLBACKS.put("SAR", new BigDecimal("8.80"));
    }

    /**
     * Verilen para birimi ve tarih için TCMB veya Frankfurter alış kurunu çeker.
     * TRY ise doğrudan 1.0 döner.
     */
    public BigDecimal getExchangeRate(String currencyCode, LocalDate date) {
        if (currencyCode == null || "TRY".equalsIgnoreCase(currencyCode.trim())) {
            return BigDecimal.ONE;
        }

        String targetCurrency = currencyCode.trim().toUpperCase(java.util.Locale.ENGLISH);
        
        // Gelecek tarihli aramaları bugüne eşitle
        if (date.isAfter(LocalDate.now())) {
            date = LocalDate.now();
        }
        
        String cacheKey = date.toString();

        // Son başarılı çekim üzerinden 1 saat geçmişse bugünün kurlarını cache'ten temizle (canlı kurların güncellenmesi için)
        if (date.equals(LocalDate.now()) && lastLiveFetchTime != null 
                && Duration.between(lastLiveFetchTime, LocalDateTime.now()).toHours() >= 1) {
            rateCache.remove(cacheKey);
            lastLiveFetchTime = null;
            log.info("Canlı kurların güncellenmesi için günlük cache temizlendi.");
        }

        // 1) Cache kontrolü
        Map<String, BigDecimal> dateRates = rateCache.get(cacheKey);
        if (dateRates != null && dateRates.containsKey(targetCurrency)) {
            return dateRates.get(targetCurrency);
        }

        // 2) Eğer tarih bugün ise, öncelikle canlı kur servislerini dene (Truncgil veya Frankfurter)
        if (date.equals(LocalDate.now())) {
            Map<String, BigDecimal> liveRates = fetchTruncgilRatesForToday();
            if (liveRates != null && liveRates.containsKey(targetCurrency)) {
                rateCache.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>()).putAll(liveRates);
                // Dinamik fallback'i güncelle
                DYNAMIC_FALLBACKS.put(targetCurrency, liveRates.get(targetCurrency));
                lastLiveFetchTime = LocalDateTime.now();
                return liveRates.get(targetCurrency);
            }
            
            // Frankfurter yedek araması (Bugün için)
            BigDecimal frankfurterRate = fetchFrankfurterRate(targetCurrency, date);
            if (frankfurterRate != null) {
                rateCache.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>()).put(targetCurrency, frankfurterRate);
                DYNAMIC_FALLBACKS.put(targetCurrency, frankfurterRate);
                lastLiveFetchTime = LocalDateTime.now();
                return frankfurterRate;
            }
        }

        // 3) TCMB Resmi XML Servisini dene (Geriye dönük taramalı)
        Map<String, BigDecimal> rates = fetchRatesForDate(date);
        if (rates != null && rates.containsKey(targetCurrency)) {
            rateCache.put(cacheKey, rates);
            // Dinamik fallback'i güncelle
            DYNAMIC_FALLBACKS.put(targetCurrency, rates.get(targetCurrency));
            return rates.get(targetCurrency);
        }

        // 4) Frankfurter ile geriye dönük arama
        BigDecimal frankfurterRate = fetchFrankfurterRate(targetCurrency, date);
        if (frankfurterRate != null) {
            rateCache.computeIfAbsent(cacheKey, k -> new ConcurrentHashMap<>()).put(targetCurrency, frankfurterRate);
            DYNAMIC_FALLBACKS.put(targetCurrency, frankfurterRate);
            return frankfurterRate;
        }

        // Hata durumunda veya kur bulunamazsa dinamik fallback veya sabit varsayılan kur değerleri
        BigDecimal fallback = DYNAMIC_FALLBACKS.getOrDefault(targetCurrency, BigDecimal.ONE);
        log.warn("Döviz kuru {} tarihi ve {} para birimi için hiçbir kaynaktan alınamadı. Fallback kur kullanılıyor: {}", 
                date, targetCurrency, fallback);

        return fallback;
    }

    private Map<String, BigDecimal> fetchRatesForDate(LocalDate date) {
        // Hafta sonları ve resmi tatillerde kur yayınlanmadığı için son 5 günü tarar
        for (int i = 0; i < 5; i++) {
            LocalDate targetDate = date.minusDays(i);
            String urlStr = getTcmbUrl(targetDate);
            try {
                Map<String, BigDecimal> rates = parseTcmbXml(urlStr);
                if (rates != null && !rates.isEmpty()) {
                    log.info("TCMB kurları başarıyla çekildi. Tarih: {}", targetDate);
                    return rates;
                }
            } catch (Exception e) {
                log.warn("TCMB kuru çekilemedi. Tarih: {} (URL: {})", targetDate, urlStr, e);
            }
        }
        return null;
    }

    private String getTcmbUrl(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "https://www.tcmb.gov.tr/kurlar/today.xml";
        }
        String yyyyMM = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String ddMMyyyy = date.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        return "https://www.tcmb.gov.tr/kurlar/" + yyyyMM + "/" + ddMMyyyy + ".xml";
    }

    private Map<String, BigDecimal> parseTcmbXml(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000); // 8 saniyeye çıkarıldı
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        try {
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new Exception("404 Bulunamadı (Hafta sonu veya resmi tatil olabilir)");
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP Hata Kodu: " + responseCode);
            }

            Map<String, BigDecimal> rates = new HashMap<>();
            try (InputStream is = conn.getInputStream()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                try {
                    // Disable external DTDs completely
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    // Disable external entities
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setXIncludeAware(false);
                    factory.setExpandEntityReferences(false);
                } catch (Exception e) {
                    log.error("XML güvenlik ayarları desteklenmiyor, TCMB kuru çekilemedi", e);
                    return null;
                }
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);
                doc.getDocumentElement().normalize();

                NodeList nodeList = doc.getElementsByTagName("Currency");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Element element = (Element) nodeList.item(i);
                    String currencyCode = element.getAttribute("CurrencyCode");
                    if ("USD".equalsIgnoreCase(currencyCode) || "EUR".equalsIgnoreCase(currencyCode) || "GBP".equalsIgnoreCase(currencyCode) || "SAR".equalsIgnoreCase(currencyCode)) {
                        NodeList forexBuyingList = element.getElementsByTagName("ForexBuying");
                        if (forexBuyingList.getLength() > 0) {
                            String forexBuyingStr = forexBuyingList.item(0).getTextContent();
                            if (forexBuyingStr != null && !forexBuyingStr.trim().isEmpty()) {
                                rates.put(currencyCode.toUpperCase(java.util.Locale.ENGLISH), new BigDecimal(forexBuyingStr.trim()));
                            }
                        }
                    }
                }
            }
            return rates;
        } finally {
            conn.disconnect();
        }
    }

    private BigDecimal fetchFrankfurterRate(String currency, LocalDate date) {
        HttpURLConnection conn = null;
        try {
            String dateStr = date.equals(LocalDate.now()) ? "latest" : date.toString();
            String urlStr = "https://api.frankfurter.app/" + dateStr + "?symbols=TRY&base=" + currency.toUpperCase(java.util.Locale.ENGLISH);
            
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream()) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(is);
                    JsonNode rates = root.path("rates");
                    if (!rates.isMissingNode() && rates.has("TRY")) {
                        BigDecimal rate = new BigDecimal(rates.get("TRY").asText());
                        log.info("Frankfurter API'sinden kur başarıyla çekildi. {} - {}: {}", dateStr, currency, rate);
                        return rate;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Frankfurter API'sinden kur çekilemedi ({} - {})", date, currency, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    public BigDecimal getGoldPrice() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://finans.truncgil.com/today.json");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream()) {
                    String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    int goldIndex = json.indexOf("Gram Altın");
                    if (goldIndex == -1) {
                        goldIndex = json.indexOf("gram-altin");
                    }
                    if (goldIndex != -1) {
                        int buyingIndex = json.indexOf("\"Alış\"", goldIndex);
                        if (buyingIndex == -1) {
                            buyingIndex = json.indexOf("\"Buying\"", goldIndex);
                        }
                        if (buyingIndex != -1) {
                            int start = json.indexOf("\"", buyingIndex + 6);
                            if (start == -1) {
                                log.warn("getGoldPrice: JSON değer başlangıcı bulunamadı");
                                return new BigDecimal("2450.00");
                            }
                            int end = json.indexOf("\"", start + 1);
                            if (end == -1) {
                                log.warn("getGoldPrice: JSON değer sonu bulunamadı");
                                return new BigDecimal("2450.00");
                            }
                            String val = json.substring(start + 1, end);
                            val = val.replace(".", "").replace(",", "."); 
                            return new BigDecimal(val.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Canlı Gram Altın fiyatı çekilemedi, hesaplama yöntemine geçiliyor", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        // Hesaplama yöntemi: Ounce Altın (~2350 USD) * Dolar Kuru / 31.1034768
        BigDecimal usdRate = getExchangeRate("USD", LocalDate.now());
        if (usdRate != null && usdRate.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal ounceUsd = new BigDecimal("2350.00"); 
            return ounceUsd.multiply(usdRate).divide(new BigDecimal("31.1034768"), 2, java.math.RoundingMode.HALF_UP);
        }

        return new BigDecimal("2450.00"); 
    }

    private Map<String, BigDecimal> fetchTruncgilRatesForToday() {
        Map<String, BigDecimal> rates = new HashMap<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://finans.truncgil.com/today.json");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream()) {
                    String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(json);
                    
                    for (String currency : new String[]{"USD", "EUR", "GBP", "SAR"}) {
                        JsonNode currencyNode = root.path(currency);
                        if (!currencyNode.isMissingNode()) {
                            String buyingStr = currencyNode.path("Alış").asText();
                            if (buyingStr == null || buyingStr.trim().isEmpty()) {
                                buyingStr = currencyNode.path("Buying").asText();
                            }
                            if (buyingStr != null && !buyingStr.trim().isEmpty()) {
                                String cleanVal = buyingStr.trim().replace(".", "").replace(",", ".");
                                rates.put(currency, new BigDecimal(cleanVal));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Truncgil canlı döviz kurları çekilemedi", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return rates;
    }
}
