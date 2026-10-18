# SKILLS — ملخص المهمة الأخيرة (للمتابعة)

## المهمة: واجهة القرآن + التفسير + إصلاح ويدجتس الصلاة + تنسيق أزرار التخصيص

### السبب الجذري للأخطاء
- ويدجت "أوقات الصلاة" و"أرقام كبيرة" كانا يفشلان ("Couldn't add widget" / "تعذّرت المعاينة") لأن التخطيطات تحتوي عنصر `<View>` عادي وهو غير مسموح داخل RemoteViews. استُبدل بـ `FrameLayout` في: wl_prayers_ltr/rtl و wl_digital_ltr/rtl.
- زرا "افتراضي/حفظ" غير منسقين لأن `Ui.text()` كان يفرض `TEXT_ALIGNMENT_VIEW_START` فيتجاهل `setGravity(CENTER)`. أُزيل السطر من Ui.text، وأُضيف توسيط صريح وحشوة للزرين في WidgetConfigActivity.

### قاعدة للمستقبل
- في تخطيطات الويدجت (res/layout/wl_*.xml) المسموح فقط: FrameLayout, LinearLayout, RelativeLayout, GridLayout, TextView, Button, ImageView, ImageButton, ProgressBar, Chronometer, TextClock, AnalogClock, ViewFlipper, ListView, GridView, StackView. ممنوع: View, Space, ScrollView, EditText.
- أي TextView يُستخدم معه setGravity للتوسيط يجب ألا يُفرض عليه TEXT_ALIGNMENT_VIEW_START.

### ما أُضيف في القرآن
- QuranTafsir.java: جلب التفسير من api.alquran.cloud (الميسر، الجلالين، القرطبي، البغوي، الوسيط، تنوير المقباس، English) مع تخزين محلي في files/tafsir/ ليعمل دون إنترنت بعد أول فتح.
- QuranTafsirActivity.java: شاشة الآية + اختيار التفسير + السابقة/التالية + نسخ ومشاركة. تُفتح من: بطاقة آية اليوم، نتائج البحث، أيقونة (i) في شريط تحديد الآية بالمصحف.
- QuranData.searchAyahs / locate: بحث في نص كل الآيات بدون تشكيل، ويدعم المرجع مثل 2:255 أو "البقرة 255".
- QuranActivity: بطاقة آية اليوم (اقرأ/تفسير)، أقسام نتائج (السور + الآيات)، صفوف آيات بخط المصحف.
- Manifest: تسجيل QuranTafsirActivity. strings: مفاتيح quran_tafsir و tafsir_names وغيرها (عربي + إنجليزي).

### لم يُختبر بعد (يحتاج بناء وتجربة على الجهاز)
- لم يُشغَّل البناء في بيئة العمل (لا يوجد Android SDK هنا)؛ شغّل build.yml وتأكد من الترجمة.
- أسماء إصدارات التفسير (ar.waseet, ar.miqbas, ar.qurtubi, ar.baghawi) تعتمد على توفرها في alquran.cloud؛ إن فشل أحدها يظهر "تعذّر التحميل" ويمكن حذفه من QuranTafsir.IDS و tafsir_names.
- قاموس معاني المفردات لكل كلمة غير مضاف؛ المضاف هو التفسير الكامل للآية.
