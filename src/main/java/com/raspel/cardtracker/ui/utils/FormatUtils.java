package com.raspel.cardtracker.ui.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import com.vaadin.flow.component.textfield.TextField;

public class FormatUtils {

    private static final Locale TR_LOCALE = Locale.of("tr", "TR");

    public static final NumberFormat TR_NUMBER_FORMAT;

    static {
        TR_NUMBER_FORMAT = NumberFormat.getNumberInstance(TR_LOCALE);
        TR_NUMBER_FORMAT.setMinimumFractionDigits(2);
        TR_NUMBER_FORMAT.setMaximumFractionDigits(2);
        TR_NUMBER_FORMAT.setGroupingUsed(true);
    }

    public static String formatNumber(BigDecimal value) {
        if (value == null) return "0,00";
        return TR_NUMBER_FORMAT.format(value);
    }

    /**
     * Formats a BigDecimal as a Turkish currency string: 10.000,00
     */
    public static String formatTurkishCurrency(BigDecimal value) {
        if (value == null) {
            return "0,00";
        }
        DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(TR_LOCALE);
        df.applyPattern("#,##0.00");
        return df.format(value);
    }

    /**
     * Parses a string in Turkish format (e.g. 10.000,50 or 10000.50) into a BigDecimal.
     */
    public static BigDecimal parseTurkishCurrency(String input) {
        if (input == null || input.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        String cleaned = input.trim();

        if (cleaned.contains(".") && cleaned.contains(",")) {
            int dotIndex = cleaned.indexOf(".");
            int commaIndex = cleaned.indexOf(",");
            if (dotIndex < commaIndex) {
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (cleaned.contains(",")) {
            int commaIdx = cleaned.lastIndexOf(",");
            int afterComma = cleaned.length() - commaIdx - 1;
            if (afterComma <= 2) {
                cleaned = cleaned.replace(",", ".");
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (cleaned.contains(".")) {
            int lastDot = cleaned.lastIndexOf(".");
            int lengthAfterDot = cleaned.length() - lastDot - 1;
            if (lengthAfterDot >= 3) {
                cleaned = cleaned.replace(".", "");
            }
        }

        cleaned = cleaned.replaceAll("[^0-9.]", "");
        cleaned = cleaned.replaceAll("\\.+$", "");
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static void attachCurrencyFormatting(TextField field) {
        field.getElement().executeJs(
            "var self = this;" +
            "var initMoney = function(){" +
            "var el = self.inputElement || (self.shadowRoot && self.shadowRoot.querySelector('input'));" +
            "if(!el) { setTimeout(initMoney, 50); return; }" +
            "if(el._moneyInit) return;" +
            "el._moneyInit = true;" +
            "el.setAttribute('inputmode','numeric');" +
            "var formatIt = function(){" +
            "  var v = el.value;" +
            "  var intPart, decPart = null;" +
            "  if(v.indexOf(',') >= 0) {" +
            "    var parts = v.split(',');" +
            "    intPart = parts[0];" +
            "    decPart = parts[parts.length - 1];" +
            "  } else if(v.indexOf('.') >= 0) {" +
            "    var lastDot = v.lastIndexOf('.');" +
            "    var afterDot = v.substring(lastDot + 1);" +
            "    if(afterDot.length <= 2) {" +
            "      intPart = v.substring(0, lastDot);" +
            "      decPart = afterDot;" +
            "    } else {" +
            "      intPart = v;" +
            "    }" +
            "  } else {" +
            "    intPart = v;" +
            "  }" +
            "  intPart = (intPart || '').replace(/[^0-9]/g, '').replace(/^0+/, '') || '0';" +
            "  intPart = intPart.replace(/\\B(?=(\\d{3})+(?!\\d))/g, '.');" +
            "  if(decPart !== null) {" +
            "    decPart = decPart.replace(/[^0-9]/g, '').substring(0, 2);" +
            "    while(decPart.length < 2) decPart += '0';" +
            "    el.value = intPart + ',' + decPart;" +
            "  } else {" +
            "    el.value = intPart;" +
            "  }" +
            "};" +
            "el.addEventListener('input', formatIt);" +
            "el.addEventListener('focus', function(){" +
            "  if(el.value === '0,00' || el.value === '0') el.value = '';" +
            "  setTimeout(function(){" +
            "    try { el.select(); } catch(e) {}" +
            "    el.selectionStart = 0; el.selectionEnd = el.value.length;" +
            "  }, 10);" +
            "}, true);" +
            "el.addEventListener('blur', function(){" +
            "  formatIt();" +
            "  if(!el.value || el.value === '0') el.value = '0,00';" +
            "}, true);" +
            "if(el.value && el.value !== '0,00') formatIt();" +
            "};" +
            "initMoney();"
        );
    }
}
