package com.raspel.cardtracker.ui;

import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.helper.Series;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "average-maturity", layout = MainLayout.class)
@PageTitle("Ortalama Vade")
@PermitAll
public class AverageMaturityView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr"));
    private static final String[] AYLAR = {"Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"};

    // CSV
    private final Grid<VadeRow> grid = new Grid<>(VadeRow.class, false);
    private List<VadeRow> allRows = new ArrayList<>(), filteredRows = new ArrayList<>();
    private List<BigDecimal> nakitList = new ArrayList<>();
    private VerticalLayout nakitContainer, csvContent;
    private Div csvResultBox, csvChartBox;

    // Manual
    private RadioButtonGroup<String> modeGroup;
    private ComboBox<Integer> countSelect;
    private VerticalLayout rowsContainer;
    private Div manualResult;
    private List<TextField> manAmt = new ArrayList<>();
    private List<ComboBox<Integer>> manDays = new ArrayList<>(), manMonths = new ArrayList<>();
    private List<IntegerField> manYears = new ArrayList<>(), manGuns = new ArrayList<>();

    public AverageMaturityView() {
        setSizeFull(); setPadding(true); setSpacing(true);
        getStyle().set("overflow", "auto");

        // Header
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull(); header.setAlignItems(Alignment.CENTER);
        H2 title = new H2("📊 Ortalama Vade Hesaplama");
        title.getStyle().set("margin","0").set("font-size","1.4em");
        Span subtitle = new Span("CSV toplu hesaplama veya manuel giriş");
        subtitle.getStyle().set("color","var(--lumo-secondary-text-color)").set("font-size","0.85em");
        VerticalLayout headerText = new VerticalLayout(title, subtitle);
        headerText.setPadding(false); headerText.setSpacing(false);
        header.add(headerText);
        header.expand(headerText);
        add(header);

        HorizontalLayout columns = new HorizontalLayout(buildCsvCard(), buildManualCard());
        columns.setWidthFull(); columns.setSpacing(true);
        columns.getStyle().set("gap","20px").set("align-items","flex-start").set("flex-wrap","wrap");
        add(columns);
        expand(columns);
    }

    // ==================== CSV CARD ====================
    private Div buildCsvCard() {
        Div card = sectionCard(null);
        card.getStyle().set("flex","1").set("min-width","420px");

        H4 t = new H4("📁 CSV ile Toplu Hesaplama");
        t.getStyle().set("margin","0 0 4px 0");

        Span desc = new Span("Banka POS raporunu yükleyin, otomatik ayrıştırılsın");
        desc.getStyle().set("font-size","0.78em").set("color","var(--lumo-tertiary-text-color)");

        MemoryBuffer buf = new MemoryBuffer();
        Upload up = new Upload(buf);
        up.setAcceptedFileTypes(".csv"); up.setMaxFiles(1);
        up.setDropLabel(new Span("CSV dosyasını sürükleyin veya seçin"));
        up.getStyle().set("margin-top","8px");

        up.addSucceededListener(e -> {
            try (InputStream is = buf.getInputStream()) {
                allRows = parseCSV(is);
                if (allRows.isEmpty()) { notifyErr("Geçerli satır bulunamadı."); return; }
                filteredRows = new ArrayList<>(allRows);
                refreshCsv();
                notifyOk(allRows.size() + " işlem okundu");
            } catch (Exception ex) { notifyErr("Dosya okunamadı."); }
        });

        configureGrid();

        DatePicker from = new DatePicker("Başlangıç"); from.setClearButtonVisible(true); from.setWidth("140px");
        DatePicker to = new DatePicker("Bitiş"); to.setClearButtonVisible(true); to.setWidth("140px");
        from.addValueChangeListener(e -> { filterCsv(from.getValue(), to.getValue()); });
        to.addValueChangeListener(e -> { filterCsv(from.getValue(), to.getValue()); });

        TextField nakitF = new TextField("Nakit (₺)"); nakitF.setWidth("130px");
        FormatUtils.attachCurrencyFormatting(nakitF);
        Button nakitAdd = new Button("Ekle", ev -> {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(nakitF.getValue());
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) { notifyErr("Tutar > 0 olmalı"); return; }
            nakitList.add(amt); refreshNakit(); nakitF.clear();
            if (!filteredRows.isEmpty() || !nakitList.isEmpty()) showCsvResult();
        });
        nakitAdd.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        Button resetCSV = new Button("Sıfırla", ev -> resetCsv());
        resetCSV.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout flt = new HorizontalLayout(from, to, nakitF, nakitAdd, resetCSV);
        flt.setWidthFull(); flt.setAlignItems(Alignment.END); flt.setSpacing(true);
        flt.getStyle().set("flex-wrap","wrap").set("gap","6px").set("margin-top","8px");

        nakitContainer = new VerticalLayout();
        nakitContainer.setPadding(false); nakitContainer.setSpacing(false);
        nakitContainer.getStyle().set("max-height","50px").set("overflow-y","auto");

        csvResultBox = new Div(); csvResultBox.setVisible(false);
        csvChartBox = new Div(); csvChartBox.setVisible(false);
        grid.setVisible(false);

        csvContent = new VerticalLayout(t, desc, up, flt, nakitContainer, csvResultBox, csvChartBox, grid);
        csvContent.setPadding(false); csvContent.setSpacing(false);
        csvContent.getStyle().set("gap","4px");

        card.add(csvContent);
        return card;
    }

    private void configureGrid() {
        grid.setWidthFull(); grid.getStyle().set("margin-top","6px");
        grid.addColumn(VadeRow::sira).setHeader("#").setWidth("40px").setFlexGrow(0);
        grid.addColumn(r -> r.tarih.format(DATE_FMT)).setHeader("Tarih").setAutoWidth(true);
        grid.addColumn(r -> FormatUtils.formatNumber(r.tutar) + " ₺").setHeader("Tutar").setAutoWidth(true);
        grid.addColumn(VadeRow::banka).setHeader("Banka").setAutoWidth(true);
        grid.addColumn(r -> r.taksit == 0 ? "Tek" : String.valueOf(r.taksit)).setHeader("Taksit").setAutoWidth(true);
        grid.addColumn(VadeRow::kartAciklama).setHeader("Açıklama").setAutoWidth(true).setFlexGrow(1);
    }

    private void filterCsv(LocalDate from, LocalDate to) {
        filteredRows = allRows.stream()
                .filter(r -> from == null || !r.tarih.isBefore(from))
                .filter(r -> to == null || !r.tarih.isAfter(to))
                .collect(java.util.stream.Collectors.toList());
        refreshCsv();
    }

    private void refreshCsv() {
        grid.setItems(filteredRows); grid.setVisible(!filteredRows.isEmpty());
        if (!filteredRows.isEmpty() || !nakitList.isEmpty()) showCsvResult();
        else { csvResultBox.setVisible(false); csvChartBox.setVisible(false); }
    }

    private List<VadeRow> parseCSV(InputStream is) throws Exception {
        List<VadeRow> rows = new ArrayList<>();
        byte[] bytes = is.readAllBytes();
        byte[] t = bytes;
        if (bytes.length >= 3 && bytes[0]==(byte)0xEF && bytes[1]==(byte)0xBB && bytes[2]==(byte)0xBF)
            t = java.util.Arrays.copyOfRange(bytes,3,bytes.length);
        String c = new String(t, StandardCharsets.UTF_8);
        if (c.contains("\uFFFD")) c = new String(t, java.nio.charset.Charset.forName("Windows-1254"));
        String[] lines = c.split("\\r?\\n"); if (lines.length < 2) return rows;
        String delim = lines[0].contains(";")?";":lines[0].contains("\t")?"\t":",";
        int sira = 0;
        for (int i=1; i<lines.length; i++) {
            String line = lines[i].trim(); if(line.isEmpty()) continue;
            String[] cols = line.split(delim,-1); if(cols.length<12) continue;
            try {
                if(!cols[10].trim().equalsIgnoreCase("Basarili") && !cols[10].trim().equalsIgnoreCase("Başarılı")) continue;
                LocalDate d = LocalDate.parse(cols[9].trim(), DATE_FMT);
                BigDecimal amt = parseAmt(cols[5].trim()); if(amt.compareTo(BigDecimal.ZERO)<=0) continue;
                rows.add(new VadeRow(++sira, d, amt, cols[3].trim(), cols[7].trim(), cols[10].trim(), pInt(cols[6].trim())));
            } catch (Exception ignored){}
        }
        return rows;
    }
    private BigDecimal parseAmt(String v) { if(v==null||v.trim().isEmpty()) return BigDecimal.ZERO; v=v.trim().replace("\"","").replace("₺","").replace("TL","").replace(" ",""); try{return new BigDecimal(v.replace(",","."));}catch(Exception e){return BigDecimal.ZERO;} }
    private int pInt(String v) { v=(v!=null?v:"").replaceAll("[^0-9]",""); try{return v.isEmpty()?0:Integer.parseInt(v);}catch(Exception e){return 0;} }

    private void showCsvResult() {
        grid.setVisible(true); BigDecimal total=BigDecimal.ZERO, weighted=BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        for (VadeRow r : filteredRows) {
            total = total.add(r.tutar);
            long d = Math.max(0, ChronoUnit.DAYS.between(today, r.tarih));
            weighted = weighted.add(r.tutar.multiply(BigDecimal.valueOf(d)));
        }
        int cashCount = nakitList.size();
        for (BigDecimal n : nakitList) total = total.add(n);
        long avgD = 0; if(total.compareTo(BigDecimal.ZERO)>0) avgD = weighted.divide(total,0,RoundingMode.HALF_UP).longValue();
        BigDecimal avgM = BigDecimal.valueOf(avgD).divide(BigDecimal.valueOf(30),1,RoundingMode.HALF_UP);

        csvResultBox.removeAll(); csvResultBox.setVisible(true);
        csvResultBox.getStyle().clear();
        csvResultBox.getStyle().set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), var(--lumo-base-color))")
                .set("border-radius","12px").set("padding","16px 20px").set("margin-top","8px")
                .set("border","1px solid var(--lumo-contrast-10pct)");

        H4 rt = new H4("📋 Sonuç"); rt.getStyle().set("margin","0 0 10px 0").set("font-size","0.95em");
        csvResultBox.add(rt);

        FlexLayout stats = new FlexLayout();
        stats.setWidthFull(); stats.getStyle().set("gap","10px").set("flex-wrap","wrap");
        stats.add(csvStatCard("📋",(filteredRows.size()+cashCount)+" işlem","#2196F3"));
        stats.add(csvStatCard("💰",FormatUtils.formatNumber(total)+" ₺","#4CAF50"));
        stats.add(csvStatCard("📅",avgD+" gün (~"+avgM.stripTrailingZeros().toPlainString()+" ay)","#FF9800"));
        stats.add(csvStatCard("🗓️","Vade: "+LocalDate.now().plusDays(avgD).format(DISPLAY_FMT),"#9C27B0"));
        csvResultBox.add(stats);

        // Chart
        int[] buckets = new int[8]; String[] bl = {"0-15 gün","16-30","31-60","61-90","91-120","121-180","181-365","365+"};
        for (VadeRow r : filteredRows) {
            long g = Math.max(0,ChronoUnit.DAYS.between(today,r.tarih));
            if(g<=15)buckets[0]++;else if(g<=30)buckets[1]++;else if(g<=60)buckets[2]++;else if(g<=90)buckets[3]++;else if(g<=120)buckets[4]++;else if(g<=180)buckets[5]++;else if(g<=365)buckets[6]++;else buckets[7]++;
        }
        csvChartBox.removeAll(); csvChartBox.setVisible(true);
        csvChartBox.getStyle().clear();
        csvChartBox.getStyle().set("background","var(--lumo-base-color)").set("border-radius","10px")
                .set("padding","12px 16px").set("margin-top","8px").set("border","1px solid var(--lumo-contrast-10pct)");
        H4 chT = new H4("📊 Vade Dağılımı"); chT.getStyle().set("margin","0 0 6px 0").set("font-size","0.9em");
        csvChartBox.add(chT);

        List<String> lbls = new ArrayList<>(); List<Double> dat = new ArrayList<>();
        for(int i=0;i<buckets.length;i++){if(buckets[i]>0){lbls.add(bl[i]);dat.add((double)buckets[i]);}}
        if(!lbls.isEmpty()){
            ApexCharts ch = ApexChartsBuilder.get()
                    .withChart(ChartBuilder.get().withType(Type.BAR).withBackground("transparent").withHeight("200px").build())
                    .withPlotOptions(PlotOptionsBuilder.get()
                            .withBar(com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder.get()
                                    .withHorizontal(false).withColumnWidth("60%").build()).build())
                    .withDataLabels(DataLabelsBuilder.get().withEnabled(true).build())
                    .withSeries(new Series<>("İşlem",dat.toArray(new Double[0])))
                    .withXaxis(XAxisBuilder.get().withCategories(lbls).build()).withColors("#2196F3").build();
            ch.setWidth("100%"); csvChartBox.add(ch);
        }
    }

    private Div csvStatCard(String icon, String val, String color) {
        Div c = new Div();
        c.getStyle().set("flex","1").set("min-width","120px").set("padding","10px 12px")
                .set("border-radius","8px").set("background","var(--lumo-base-color)")
                .set("border-left","3px solid "+color).set("text-align","center");
        Span v = new Span(icon+" "+val);
        v.getStyle().set("font-size","0.82em").set("font-weight","700").set("color",color).set("display","block");
        c.add(v); return c;
    }

    private void refreshNakit() {
        nakitContainer.removeAll();
        for(int i=0;i<nakitList.size();i++){
            int idx=i; BigDecimal amt=nakitList.get(i);
            HorizontalLayout r=new HorizontalLayout(); r.setAlignItems(Alignment.CENTER);
            r.getStyle().set("gap","4px").set("font-size","0.75em");
            Span l=new Span("Nakit #"+(i+1)+": "+FormatUtils.formatNumber(amt)+" ₺");
            Button x=new Button(new Icon(VaadinIcon.CLOSE_SMALL),ev->{nakitList.remove(idx);refreshNakit();if(!filteredRows.isEmpty()||!nakitList.isEmpty())showCsvResult();});
            x.addThemeVariants(ButtonVariant.LUMO_SMALL,ButtonVariant.LUMO_TERTIARY,ButtonVariant.LUMO_ERROR);
            r.add(l,x); r.expand(l); nakitContainer.add(r);
        }
    }

    private void resetCsv() {
        allRows.clear();filteredRows.clear();nakitList.clear();nakitContainer.removeAll();
        grid.setItems(filteredRows);grid.setVisible(false);
        csvResultBox.setVisible(false);csvResultBox.removeAll();
        csvChartBox.setVisible(false);csvChartBox.removeAll();
    }

    // ==================== MANUAL CARD ====================
    private Div buildManualCard() {
        Div card = sectionCard(null);
        card.getStyle().set("flex","0 0 400px").set("min-width","360px");

        H4 t = new H4("✏️ Manuel Hesaplama");
        t.getStyle().set("margin","0 0 8px 0");

        modeGroup = new RadioButtonGroup<>();
        modeGroup.setItems("📅 Tarih girerek", "🔢 Gün girerek");
        modeGroup.setValue("📅 Tarih girerek");
        modeGroup.getStyle().set("font-size","0.85em");
        modeGroup.addValueChangeListener(e -> rebuildRows());

        countSelect = new ComboBox<>("Ödeme sayısı");
        countSelect.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
        countSelect.setValue(2); countSelect.setWidth("130px");
        countSelect.addValueChangeListener(e -> rebuildRows());

        rowsContainer = new VerticalLayout();
        rowsContainer.setPadding(false); rowsContainer.setSpacing(false);
        rowsContainer.getStyle().set("gap","8px").set("margin-top","8px");

        manualResult = new Div();
        manualResult.getStyle().set("padding","12px 16px").set("border-radius","10px")
                .set("text-align","center").set("font-size","0.9em").set("margin-top","10px");

        Button calcBtn = new Button("Hesapla", e -> calcManual());
        calcBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY); calcBtn.setWidthFull();
        Button resetBtn = new Button("Sıfırla", e -> resetManual());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY); resetBtn.setWidthFull();

        card.add(t, modeGroup, countSelect, rowsContainer, calcBtn, resetBtn, manualResult);
        rebuildRows();
        return card;
    }

    private void rebuildRows() {
        rowsContainer.removeAll();
        manDays.clear(); manMonths.clear(); manYears.clear(); manGuns.clear(); manAmt.clear();
        int n = countSelect.getValue() != null ? countSelect.getValue() : 2;
        boolean dateMode = "📅 Tarih girerek".equals(modeGroup.getValue());

        for (int i = 0; i < n; i++) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull(); row.setAlignItems(Alignment.END);
            row.getStyle().set("gap","6px").set("padding","6px 8px")
                    .set("border-radius","8px").set("background","var(--lumo-contrast-5pct)");

            Span num = new Span(String.valueOf(i+1));
            num.getStyle().set("font-weight","700").set("font-size","0.9em")
                    .set("min-width","20px").set("color","var(--lumo-primary-text-color)");

            if (dateMode) {
                ComboBox<Integer> day = new ComboBox<>();
                day.setItems(java.util.stream.IntStream.rangeClosed(1,31).boxed().toList());
                day.setValue(15); day.setWidth("58px");
                ComboBox<Integer> mon = new ComboBox<>();
                mon.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
                mon.setItemLabelGenerator(m -> AYLAR[m-1]); mon.setValue(6); mon.setWidth("105px");
                IntegerField yr = new IntegerField();
                yr.setValue(LocalDate.now().getYear()); yr.setWidth("80px");
                row.add(num, day, mon, yr);
                manDays.add(day); manMonths.add(mon); manYears.add(yr);
            } else {
                IntegerField gun = new IntegerField();
                gun.setMin(1); gun.setValue(30); gun.setWidth("80px");
                Span gl = new Span("gün"); gl.getStyle().set("font-size","0.8em").set("color","var(--lumo-secondary-text-color)");
                row.add(num, gun, gl);
                manGuns.add(gun);
            }

            TextField amtF = new TextField();
            amtF.setValue("0,00"); amtF.setWidth("115px");
            FormatUtils.attachCurrencyFormatting(amtF);
            Span tl = new Span("₺"); tl.getStyle().set("font-size","0.85em").set("color","var(--lumo-secondary-text-color)");
            row.add(amtF, tl);
            manAmt.add(amtF);
            rowsContainer.add(row);
        }
    }

    private void calcManual() {
        boolean dateMode = "📅 Tarih girerek".equals(modeGroup.getValue());
        int n = countSelect.getValue();
        List<BigDecimal> amts = new ArrayList<>();
        List<Long> days = new ArrayList<>();
        StringBuilder err = new StringBuilder();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < n; i++) {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(manAmt.get(i).getValue());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { err.append(i+1).append(". tutar > 0. "); continue; }
            amts.add(amt);

            if (dateMode) {
                try {
                    LocalDate dt = LocalDate.of(manYears.get(i).getValue(), manMonths.get(i).getValue(), manDays.get(i).getValue());
                    long dd = ChronoUnit.DAYS.between(today, dt);
                    days.add(Math.max(0, dd));
                } catch (Exception e) { err.append(i+1).append(". geçersiz tarih. "); }
            } else {
                long g = manGuns.get(i).getValue();
                if (g <= 0) { err.append(i+1).append(". gün > 0. "); continue; }
                days.add(g);
            }
        }

        if (err.length() > 0) {
            manualResult.getStyle().set("background","var(--lumo-error-color-10pct)").set("color","var(--lumo-error-color)");
            manualResult.setText("❌ " + err.toString()); return;
        }
        if (amts.isEmpty()) {
            manualResult.getStyle().set("background","var(--lumo-error-color-10pct)").set("color","var(--lumo-error-color)");
            manualResult.setText("❌ Geçerli ödeme yok."); return;
        }

        BigDecimal total = BigDecimal.ZERO, w = BigDecimal.ZERO;
        for (int i = 0; i < amts.size(); i++) { total = total.add(amts.get(i)); w = w.add(amts.get(i).multiply(BigDecimal.valueOf(days.get(i)))); }
        long avgD = w.divide(total, 0, RoundingMode.HALF_UP).longValue();
        BigDecimal avgM = BigDecimal.valueOf(avgD).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);

        manualResult.getStyle().set("background","linear-gradient(135deg, #E8F5E9, #C8E6C9)")
                .set("color","#2E7D32").set("border","1px solid #A5D6A7");

        if (dateMode) {
            manualResult.setText("🗓️ Ortalama Vade Tarihi\n" + today.plusDays(avgD).format(DISPLAY_FMT) + "\n(" + avgD + " gün · ~" + avgM.stripTrailingZeros().toPlainString() + " ay)");
        } else {
            manualResult.setText("🔢 Ortalama Vade\n" + avgD + " gün (~" + avgM.stripTrailingZeros().toPlainString() + " ay)");
        }
    }

    private void resetManual() {
        manualResult.getStyle().clear(); manualResult.setText("");
        countSelect.setValue(2); modeGroup.setValue("📅 Tarih girerek"); rebuildRows();
    }

    // ==================== HELPERS ====================
    private Div sectionCard(String title) {
        Div c = new Div();
        c.getStyle().set("background","var(--lumo-base-color)").set("border-radius","14px")
                .set("padding","16px 20px").set("box-shadow","0 2px 10px rgba(0,0,0,0.07)")
                .set("border","1px solid var(--lumo-contrast-10pct)");
        return c;
    }

    private void notifyOk(String msg) { Notification.show(msg, 3000, Notification.Position.BOTTOM_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS); }
    private void notifyErr(String msg) { Notification.show(msg, 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); }

    public record VadeRow(int sira, LocalDate tarih, BigDecimal tutar, String banka, String kartAciklama, String islemSonucu, int taksit) {}
}
