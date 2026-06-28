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

    // --- CSV ---
    private final Grid<VadeRow> grid = new Grid<>(VadeRow.class, false);
    private List<VadeRow> allRows = new ArrayList<>(), filteredRows = new ArrayList<>();
    private VerticalLayout csvRowsContainer;
    private Div csvResultBox, csvChartBox;

    // --- Manual ---
    private RadioButtonGroup<String> modeGroup;
    private ComboBox<Integer> countSelect;
    private VerticalLayout manRowsContainer;
    private Div manualResultBox;
    private final List<TextField> manAmt = new ArrayList<>();
    private final List<ComboBox<Integer>> manDays = new ArrayList<>(), manMonths = new ArrayList<>();
    private final List<IntegerField> manYears = new ArrayList<>(), manGuns = new ArrayList<>();

    public AverageMaturityView() {
        setSizeFull(); setPadding(true); setSpacing(false);
        getStyle().set("overflow", "hidden");

        add(buildHeader());

        HorizontalLayout mainLayout = new HorizontalLayout(buildCsvPanel(), buildManualPanel());
        mainLayout.setSizeFull(); mainLayout.setPadding(false);
        mainLayout.setSpacing(true);
        mainLayout.getStyle().set("gap","1rem").set("align-items","flex-start").set("overflow","hidden");
        add(mainLayout);
        expand(mainLayout);
    }

    // ==================== HEADER ====================
    private HorizontalLayout buildHeader() {
        Div icon = new Div();
        icon.addClassName("header-icon");
        icon.add(VaadinIcon.CALC.create());

        H2 title = new H2("Ortalama vade hesaplama");
        title.getStyle().set("margin","0").set("font-size","1.3em");

        Paragraph sub = new Paragraph("CSV ile toplu hesaplama veya manuel giriş");
        sub.getStyle().set("font-size","0.8em").set("color","var(--lumo-tertiary-text-color)").set("margin","0");

        VerticalLayout texts = new VerticalLayout(title, sub);
        texts.setPadding(false); texts.setSpacing(false);

        HorizontalLayout h = new HorizontalLayout(icon, texts);
        h.setAlignItems(Alignment.CENTER); h.setSpacing(true);
        h.getStyle().set("margin-bottom","16px");
        return h;
    }

    // Left panel — full remaining width
    private VerticalLayout buildCsvPanel() {
        VerticalLayout p = new VerticalLayout();
        p.addClassName("maturity-card");
        p.setSizeFull();
        p.setPadding(false); p.setSpacing(false);
        p.getStyle().set("min-width","0").set("overflow","hidden");
        setFlexGrow(1, p);

        // Header
        Div header = new Div(); header.addClassName("maturity-card-header");
        Div hIcon = new Div(); hIcon.addClassName("header-icon"); hIcon.add(VaadinIcon.FILE_TABLE.create());
        VerticalLayout hTexts = new VerticalLayout(
                new H4("CSV ile toplu hesaplama"),
                new Span("Banka POS raporunu yükleyin, otomatik ayrıştırılsın") {{
                    getStyle().set("font-size","0.72em").set("color","var(--lumo-tertiary-text-color)");
                }}
        );
        hTexts.setPadding(false); hTexts.setSpacing(false);
        header.add(hIcon, hTexts);
        p.add(header);

        // Upload
        MemoryBuffer buf = new MemoryBuffer();
        Upload up = new Upload(buf);
        up.setAcceptedFileTypes(".csv"); up.setMaxFiles(1);
        up.setWidthFull();
        up.setDropLabel(new Span("Dosya seç ya da buraya sürükle"));
        up.addClassName("upload-zone");

        up.addSucceededListener(e -> {
            try (InputStream is = buf.getInputStream()) {
                allRows = parseCSV(is);
                if (allRows.isEmpty()) { notifyErr("Geçerli satır bulunamadı."); return; }
                filteredRows = new ArrayList<>(allRows);
                refreshCsvGrid();
                notifyOk(allRows.size() + " işlem okundu");
            } catch (Exception ex) { notifyErr("Dosya okunamadı."); }
        });

        VerticalLayout upWrap = new VerticalLayout(up);
        upWrap.setPadding(false); upWrap.setSpacing(false);
        upWrap.getStyle().set("margin","12px 0");
        p.add(upWrap);

        // Divider
        Span divider = new Span("veya manuel giriş");
        divider.addClassName("divider-label");
        p.add(divider);

        // Grid
        configureGrid();
        grid.setVisible(false);
        p.add(grid);

        // Manual rows container (date + nakit)
        csvRowsContainer = new VerticalLayout();
        csvRowsContainer.setPadding(false); csvRowsContainer.setSpacing(false);
        csvRowsContainer.getStyle().set("gap","6px").set("max-height","200px").set("overflow-y","auto");
        p.add(csvRowsContainer);

        // Column headers
        HorizontalLayout colHdr = new HorizontalLayout();
        colHdr.setWidthFull(); colHdr.setAlignItems(Alignment.CENTER);
        colHdr.getStyle().set("gap","8px");
        Span s1 = new Span("Başlangıç"); s1.addClassName("col-label"); s1.getStyle().set("flex-grow","1");
        Span s2 = new Span("Bitiş"); s2.addClassName("col-label"); s2.getStyle().set("flex-grow","1");
        Span s3 = new Span("Tutar (₺)"); s3.addClassName("col-label"); s3.getStyle().set("width","110px").set("flex-shrink","0");
        colHdr.add(s1, s2, s3);
        csvRowsContainer.add(colHdr);

        // Initial row
        addCsvRow();

        // Buttons
        Button addRowBtn = new Button("+ Satır ekle", ev -> addCsvRow());
        addRowBtn.addClassName("btn-add-row");
        Button resetBtn = new Button("Sıfırla", ev -> resetCsv());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout btns = new HorizontalLayout(addRowBtn, resetBtn);
        btns.setSpacing(true); btns.getStyle().set("margin-top","8px");
        p.add(btns);

        // Result
        csvResultBox = new Div(); csvResultBox.setVisible(false);
        csvChartBox = new Div(); csvChartBox.setVisible(false);
        p.add(csvResultBox, csvChartBox);

        return p;
    }

    private void addCsvRow() {
        int idx = csvRowsContainer.getComponentCount();

        DatePicker from = new DatePicker(); from.setClearButtonVisible(true);
        from.setWidthFull(); from.getStyle().set("flex-grow","1").set("min-width","120px");
        DatePicker to = new DatePicker(); to.setClearButtonVisible(true);
        to.setWidthFull(); to.getStyle().set("flex-grow","1").set("min-width","120px");
        TextField amtF = new TextField(); amtF.setValue("0,00");
        amtF.setWidth("110px"); amtF.getStyle().set("flex-shrink","0");
        FormatUtils.attachCurrencyFormatting(amtF);

        Span num = new Span(String.valueOf(idx));
        num.addClassName("row-num");

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull(); row.setAlignItems(Alignment.END);
        row.getStyle().set("gap","6px");

        Button delBtn = new Button(new Icon(VaadinIcon.TRASH), ev -> {
            csvRowsContainer.remove(row);
            recalcCsv();
        });
        delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delBtn.setWidth("36px"); delBtn.getStyle().set("flex-shrink","0");

        row.add(num, from, to, amtF, delBtn);

        from.addValueChangeListener(e -> recalcCsv());
        to.addValueChangeListener(e -> recalcCsv());
        amtF.addValueChangeListener(e -> recalcCsv());

        csvRowsContainer.add(row);
    }

    private void recalcCsv() {
        BigDecimal total = BigDecimal.ZERO, weighted = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        int count = 0;

        for (int i = 1; i < csvRowsContainer.getComponentCount(); i++) {
            HorizontalLayout row = (HorizontalLayout) csvRowsContainer.getComponentAt(i);
            DatePicker from = (DatePicker) row.getComponentAt(1);
            DatePicker to = (DatePicker) row.getComponentAt(2);
            TextField amtF = (TextField) row.getComponentAt(3);

            BigDecimal amt = FormatUtils.parseTurkishCurrency(amtF.getValue());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
            total = total.add(amt); count++;

            if (from.getValue() != null && to.getValue() != null) {
                long days = Math.max(0, ChronoUnit.DAYS.between(today, to.getValue()));
                weighted = weighted.add(amt.multiply(BigDecimal.valueOf(days)));
            } else if (to.getValue() != null) {
                long days = Math.max(0, ChronoUnit.DAYS.between(today, to.getValue()));
                weighted = weighted.add(amt.multiply(BigDecimal.valueOf(days)));
            }
        }

        // Combine with parsed CSV rows
        for (VadeRow r : filteredRows) {
            total = total.add(r.tutar); count++;
            long days = Math.max(0, ChronoUnit.DAYS.between(today, r.tarih));
            weighted = weighted.add(r.tutar.multiply(BigDecimal.valueOf(days)));
        }

        if (count == 0 || total.compareTo(BigDecimal.ZERO) <= 0) {
            csvResultBox.setVisible(false); csvChartBox.setVisible(false);
            return;
        }

        long avgD = weighted.divide(total, 0, RoundingMode.HALF_UP).longValue();
        BigDecimal avgM = BigDecimal.valueOf(avgD).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);

        csvResultBox.removeAll(); csvResultBox.setVisible(true);
        csvResultBox.addClassName("maturity-result");
        csvResultBox.getClassNames().remove("error");
        csvResultBox.addClassName("success");
        csvResultBox.setText("📋 " + count + " işlem · 💰 " + FormatUtils.formatNumber(total) + " ₺\n📅 Ortalama vade: " + avgD + " gün (~" + avgM.stripTrailingZeros().toPlainString() + " ay)\n🗓️ Vade tarihi: " + today.plusDays(avgD).format(DISPLAY_FMT));

        showCsvChart();
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(VadeRow::sira).setHeader("#").setWidth("40px").setFlexGrow(0);
        grid.addColumn(r -> r.tarih.format(DATE_FMT)).setHeader("Tarih").setAutoWidth(true);
        grid.addColumn(r -> FormatUtils.formatNumber(r.tutar) + " ₺").setHeader("Tutar").setAutoWidth(true);
        grid.addColumn(VadeRow::banka).setHeader("Banka").setAutoWidth(true);
        grid.addColumn(r -> r.taksit == 0 ? "Tek" : String.valueOf(r.taksit)).setHeader("Taksit").setAutoWidth(true);
        grid.addColumn(VadeRow::kartAciklama).setHeader("Açıklama").setAutoWidth(true).setFlexGrow(1);
    }

    private void refreshCsvGrid() {
        grid.setItems(filteredRows);
        grid.setVisible(!filteredRows.isEmpty());
        recalcCsv();
    }

    private void showCsvChart() {
        csvChartBox.removeAll(); csvChartBox.setVisible(true);
        csvChartBox.getStyle().set("margin-top","8px");

        LocalDate today = LocalDate.now();
        int[] buckets = new int[8];
        String[] bl = {"0-15","16-30","31-60","61-90","91-120","121-180","181-365","365+"};
        for (VadeRow r : filteredRows) {
            long g = Math.max(0, ChronoUnit.DAYS.between(today, r.tarih));
            if (g<=15)buckets[0]++; else if(g<=30)buckets[1]++; else if(g<=60)buckets[2]++;
            else if(g<=90)buckets[3]++; else if(g<=120)buckets[4]++; else if(g<=180)buckets[5]++;
            else if(g<=365)buckets[6]++; else buckets[7]++;
        }

        List<String> lbs = new ArrayList<>(); List<Double> dat = new ArrayList<>();
        for (int i = 0; i < buckets.length; i++) { if (buckets[i] > 0) { lbs.add(bl[i]); dat.add((double)buckets[i]); } }

        if (!lbs.isEmpty()) {
            Div chWrap = new Div();
            chWrap.addClassName("maturity-card");
            chWrap.getStyle().set("padding","12px 16px");

            H4 chT = new H4("Vade Dağılımı"); chT.getStyle().set("margin","0 0 6px 0").set("font-size","0.9em");
            chWrap.add(chT);

            ApexCharts ch = ApexChartsBuilder.get()
                    .withChart(ChartBuilder.get().withType(Type.BAR).withBackground("transparent").withHeight("180px").build())
                    .withPlotOptions(PlotOptionsBuilder.get()
                            .withBar(com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder.get()
                                    .withHorizontal(false).withColumnWidth("60%").build()).build())
                    .withDataLabels(DataLabelsBuilder.get().withEnabled(true).build())
                    .withSeries(new Series<>("İşlem", dat.toArray(new Double[0])))
                    .withXaxis(XAxisBuilder.get().withCategories(lbs).build()).withColors("#2196F3").build();
            ch.setWidth("100%");
            chWrap.add(ch);
            csvChartBox.add(chWrap);
        }
    }

    private List<VadeRow> parseCSV(InputStream is) throws Exception {
        List<VadeRow> rows = new ArrayList<>();
        byte[] bytes = is.readAllBytes();
        byte[] t = bytes;
        if (bytes.length>=3 && bytes[0]==(byte)0xEF && bytes[1]==(byte)0xBB && bytes[2]==(byte)0xBF)
            t = java.util.Arrays.copyOfRange(bytes,3,bytes.length);
        String c = new String(t,StandardCharsets.UTF_8);
        if(c.contains("\uFFFD")) c = new String(t,java.nio.charset.Charset.forName("Windows-1254"));
        String[] lines = c.split("\\r?\\n"); if(lines.length<2) return rows;
        String delim = lines[0].contains(";")?";":lines[0].contains("\t")?"\t":",";
        int sira=0;
        for(int i=1;i<lines.length;i++){
            String line=lines[i].trim();if(line.isEmpty())continue;
            String[] cols=line.split(delim,-1);if(cols.length<12)continue;
            try{
                if(!cols[10].trim().equalsIgnoreCase("Basarili")&&!cols[10].trim().equalsIgnoreCase("Başarılı"))continue;
                LocalDate d=LocalDate.parse(cols[9].trim(),DATE_FMT);
                BigDecimal amt=parseAmt(cols[5].trim());if(amt.compareTo(BigDecimal.ZERO)<=0)continue;
                rows.add(new VadeRow(++sira,d,amt,cols[3].trim(),cols[7].trim(),cols[10].trim(),pInt(cols[6].trim())));
            }catch(Exception ignored){}
        }
        return rows;
    }
    private BigDecimal parseAmt(String v){if(v==null||v.trim().isEmpty())return BigDecimal.ZERO;v=v.trim().replace("\"","").replace("₺","").replace("TL","").replace(" ","");try{return new BigDecimal(v.replace(",","."));}catch(Exception e){return BigDecimal.ZERO;}}
    private int pInt(String v){v=(v!=null?v:"").replaceAll("[^0-9]","");try{return v.isEmpty()?0:Integer.parseInt(v);}catch(Exception e){return 0;}}

    private void resetCsv() {
        allRows.clear();filteredRows.clear();
        grid.setItems(filteredRows);grid.setVisible(false);
        csvResultBox.setVisible(false);csvResultBox.removeAll();
        csvChartBox.setVisible(false);csvChartBox.removeAll();
        while(csvRowsContainer.getComponentCount() > 1) csvRowsContainer.remove(csvRowsContainer.getComponentAt(csvRowsContainer.getComponentCount()-1));
    }

    // ==================== MANUAL PANEL ====================
    private VerticalLayout buildManualPanel() {
        VerticalLayout p = new VerticalLayout();
        p.addClassName("maturity-card");
        p.setPadding(false); p.setSpacing(false);
        p.setWidth("360px"); p.setMinWidth("360px"); p.setMaxWidth("360px");
        p.getStyle().set("flex-shrink","0").set("overflow","hidden");

        // Header
        Div header = new Div(); header.addClassName("maturity-card-header");
        Div hIcon = new Div(); hIcon.addClassName("header-icon"); hIcon.add(VaadinIcon.EDIT.create());
        VerticalLayout hTexts = new VerticalLayout(
                new H4("Manuel hesaplama"),
                new Span("Tarih veya gün girerek hesaplayın") {{
                    getStyle().set("font-size","0.72em").set("color","var(--lumo-tertiary-text-color)");
                }}
        );
        hTexts.setPadding(false); hTexts.setSpacing(false);
        header.add(hIcon, hTexts);
        p.add(header);

        // Radio tabs
        Div radioTabs = new Div(); radioTabs.addClassName("radio-tabs");
        Span tabDate = new Span("📅 Tarih"); tabDate.addClassName("radio-tab"); tabDate.addClassName("active");
        Span tabDay = new Span("🔢 Gün"); tabDay.addClassName("radio-tab");
        tabDate.addClickListener(e -> { tabDate.addClassName("active"); tabDay.removeClassName("active"); rebuildManRows(true); });
        tabDay.addClickListener(e -> { tabDay.addClassName("active"); tabDate.removeClassName("active"); rebuildManRows(false); });
        radioTabs.add(tabDate, tabDay);
        radioTabs.getStyle().set("margin","12px 0");

        p.add(radioTabs);

        // Count select
        countSelect = new ComboBox<>("Ödeme sayısı");
        countSelect.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
        countSelect.setValue(2); countSelect.setWidthFull();
        countSelect.addValueChangeListener(e -> rebuildManRows(tabDate.hasClassName("active")));
        p.add(countSelect);

        // Rows
        manRowsContainer = new VerticalLayout();
        manRowsContainer.setPadding(false); manRowsContainer.setSpacing(false);
        manRowsContainer.getStyle().set("gap","8px").set("margin-top","8px");
        p.add(manRowsContainer);

        // Buttons
        Button calcBtn = new Button("Hesapla", e -> calcManual(tabDate.hasClassName("active")));
        calcBtn.addClassName("btn-primary-full");
        Button resetBtn = new Button("Sıfırla", e -> resetManual());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        resetBtn.setWidthFull();

        p.add(calcBtn, resetBtn);

        // Result
        manualResultBox = new Div();
        manualResultBox.addClassName("maturity-result");
        manualResultBox.setVisible(false);
        p.add(manualResultBox);

        rebuildManRows(true);
        return p;
    }

    private void rebuildManRows(boolean dateMode) {
        manRowsContainer.removeAll();
        manDays.clear(); manMonths.clear(); manYears.clear(); manGuns.clear(); manAmt.clear();
        int n = countSelect.getValue() != null ? countSelect.getValue() : 2;

        for (int i = 0; i < n; i++) {
            HorizontalLayout row = new HorizontalLayout();
            row.addClassName("manual-row");
            row.setWidthFull(); row.setAlignItems(Alignment.BASELINE);
            row.getStyle().set("gap","6px").set("overflow","hidden");

            Span num = new Span(String.valueOf(i+1));
            num.addClassName("row-num");
            num.setWidth("22px"); num.getStyle().set("flex-shrink","0");
            row.add(num);

            if (dateMode) {
                ComboBox<Integer> day = new ComboBox<>();
                day.setItems(java.util.stream.IntStream.rangeClosed(1,31).boxed().toList());
                day.setValue(15); day.setWidth("58px"); day.getStyle().set("flex-shrink","0");
                ComboBox<Integer> mon = new ComboBox<>();
                mon.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
                mon.setItemLabelGenerator(m -> AYLAR[m-1]); mon.setValue(6);
                mon.setWidth("90px"); mon.getStyle().set("flex-shrink","0");
                IntegerField yr = new IntegerField();
                yr.setValue(LocalDate.now().getYear());
                yr.setWidth("68px"); yr.getStyle().set("flex-shrink","0");
                row.add(day, mon, yr);
                manDays.add(day); manMonths.add(mon); manYears.add(yr);
            } else {
                IntegerField gun = new IntegerField();
                gun.setMin(1); gun.setValue(30);
                gun.setWidth("60px"); gun.getStyle().set("flex-shrink","0");
                Span gl = new Span("gün");
                gl.getStyle().set("font-size","0.75em").set("color","var(--lumo-tertiary-text-color)").set("flex-shrink","0");
                row.add(gun, gl);
                manGuns.add(gun);
            }

            TextField amtF = new TextField();
            amtF.setValue("0,00"); amtF.setWidth("90px");
            amtF.getStyle().set("flex-shrink","0");
            FormatUtils.attachCurrencyFormatting(amtF);
            Span tl = new Span("₺");
            tl.getStyle().set("font-size","0.8em").set("color","var(--lumo-tertiary-text-color)").set("flex-shrink","0");
            row.add(amtF, tl);
            manAmt.add(amtF);

            manRowsContainer.add(row);
        }
    }

    private void calcManual(boolean dateMode) {
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
                    days.add(Math.max(0, ChronoUnit.DAYS.between(today, dt)));
                } catch (Exception e) { err.append(i+1).append(". geçersiz tarih. "); }
            } else {
                long g = manGuns.get(i).getValue();
                if (g <= 0) { err.append(i+1).append(". gün > 0. "); continue; }
                days.add(g);
            }
        }

        manualResultBox.setVisible(true);
        if (err.length() > 0) {
            manualResultBox.removeClassName("success"); manualResultBox.addClassName("error");
            manualResultBox.setText("❌ " + err.toString()); return;
        }
        if (amts.isEmpty()) {
            manualResultBox.removeClassName("success"); manualResultBox.addClassName("error");
            manualResultBox.setText("❌ Geçerli ödeme yok."); return;
        }

        BigDecimal total = BigDecimal.ZERO, w = BigDecimal.ZERO;
        for (int i = 0; i < amts.size(); i++) { total = total.add(amts.get(i)); w = w.add(amts.get(i).multiply(BigDecimal.valueOf(days.get(i)))); }
        long avgD = w.divide(total, 0, RoundingMode.HALF_UP).longValue();
        BigDecimal avgM = BigDecimal.valueOf(avgD).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);

        manualResultBox.removeClassName("error"); manualResultBox.addClassName("success");
        if (dateMode) {
            manualResultBox.setText("🗓️ Ortalama Vade Tarihi\n" + today.plusDays(avgD).format(DISPLAY_FMT) + "\n(" + avgD + " gün · ~" + avgM.stripTrailingZeros().toPlainString() + " ay)");
        } else {
            manualResultBox.setText("🔢 Ortalama Vade\n" + avgD + " gün (~" + avgM.stripTrailingZeros().toPlainString() + " ay)");
        }
    }

    private void resetManual() {
        manualResultBox.setVisible(false); manualResultBox.removeClassName("success"); manualResultBox.removeClassName("error");
        countSelect.setValue(2); rebuildManRows(true);
    }

    // ==================== HELPERS ====================
    private void notifyOk(String msg) { Notification.show(msg, 3000, Notification.Position.BOTTOM_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS); }
    private void notifyErr(String msg) { Notification.show(msg, 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); }

    public record VadeRow(int sira, LocalDate tarih, BigDecimal tutar, String banka, String kartAciklama, String islemSonucu, int taksit) {}
}
