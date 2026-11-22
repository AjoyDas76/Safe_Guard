# -*- coding: utf-8 -*-
import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, HRFlowable
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

# Register Bengali & Unicode Fonts
BENGALI_FONT_PATH = "/usr/share/fonts/truetype/fonts-beng-extra/Mukti.ttf"
BENGALI_BOLD_PATH = "/usr/share/fonts/truetype/fonts-beng-extra/Muktibold.ttf"

if os.path.exists(BENGALI_FONT_PATH):
    pdfmetrics.registerFont(TTFont('Mukti', BENGALI_FONT_PATH))
    font_normal = 'Mukti'
else:
    font_normal = 'Helvetica'

if os.path.exists(BENGALI_BOLD_PATH):
    pdfmetrics.registerFont(TTFont('MuktiBold', BENGALI_BOLD_PATH))
    font_bold = 'MuktiBold'
else:
    font_bold = font_normal

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super(NumberedCanvas, self).__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super(NumberedCanvas, self).showPage()
        super(NumberedCanvas, self).save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        self.setFont(font_normal, 8)
        self.setFillColor(colors.HexColor("#64748B"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(54, 750, "Smart Safety Vest Monitoring Platform — Supervisor Project Report")
            self.setStrokeColor(colors.HexColor("#CBD5E1"))
            self.setLineWidth(0.5)
            self.line(54, 744, 558, 744)

        # Footer
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(54, 45, 558, 45)
        
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawString(54, 32, "Confidential • IoT Smart Safety Vest System")
        self.drawRightString(558, 32, page_str)
        self.restoreState()

def build_pdf(filename="Smart_Safety_Vest_Features_Report.pdf"):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )

    styles = getSampleStyleSheet()

    # Custom styles
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName=font_bold,
        fontSize=20,
        leading=24,
        textColor=colors.HexColor("#0F172A"),
        spaceAfter=4
    )

    subtitle_style = ParagraphStyle(
        'DocSubTitle',
        parent=styles['Normal'],
        fontName=font_bold,
        fontSize=12,
        leading=16,
        textColor=colors.HexColor("#0284C7"),
        spaceAfter=12
    )

    meta_style = ParagraphStyle(
        'MetaStyle',
        parent=styles['Normal'],
        fontName=font_normal,
        fontSize=9,
        leading=13,
        textColor=colors.HexColor("#475569")
    )

    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Normal'],
        fontName=font_bold,
        fontSize=13,
        leading=17,
        textColor=colors.HexColor("#0F172A"),
        spaceBefore=12,
        spaceAfter=6
    )

    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Normal'],
        fontName=font_bold,
        fontSize=11,
        leading=15,
        textColor=colors.HexColor("#0369A1"),
        spaceBefore=8,
        spaceAfter=4
    )

    body_style = ParagraphStyle(
        'Body_Custom',
        parent=styles['Normal'],
        fontName=font_normal,
        fontSize=9.5,
        leading=14,
        textColor=colors.HexColor("#334155"),
        spaceAfter=6
    )

    bullet_style = ParagraphStyle(
        'Bullet_Custom',
        parent=styles['Normal'],
        fontName=font_normal,
        fontSize=9,
        leading=13.5,
        textColor=colors.HexColor("#1E293B"),
        leftIndent=14,
        firstLineIndent=-10,
        spaceAfter=4
    )

    table_header_style = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName=font_bold,
        fontSize=9,
        leading=12,
        textColor=colors.white
    )

    table_cell_style = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontName=font_normal,
        fontSize=8.5,
        leading=11.5,
        textColor=colors.HexColor("#1E293B")
    )

    callout_style = ParagraphStyle(
        'Callout',
        parent=styles['Normal'],
        fontName=font_normal,
        fontSize=9,
        leading=13,
        textColor=colors.HexColor("#0F766E")
    )

    story = []

    # Title Banner Block
    story.append(Paragraph("Smart Safety Vest Monitoring System", title_style))
    story.append(Paragraph("IoT Worker Telemetry, Spatial Geofencing & Site Safety Platform", subtitle_style))
    
    # Meta Information Box
    meta_text = (
        "<b>প্রজেক্ট ধরন:</b> IoT & Android Command Center | "
        "<b>ডকুমেন্ট:</b> সুপারভাইজার প্রেজেন্টেশন ও ফিচার রিপোর্ট | "
        "<b>তারিখ:</b> September 2026 | "
        "<b>ভার্সন:</b> v2.4 (Production Ready)"
    )
    story.append(Paragraph(meta_text, meta_style))
    story.append(Spacer(1, 10))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor("#0284C7"), spaceAfter=14))

    # Executive Summary
    story.append(Paragraph("১. এক্সিকিউটিভ সামারি (Executive Summary)", h1_style))
    exec_summary = (
        "এই অ্যাপ্লিকেশনটি ঝুঁকিপূর্ণ কর্মক্ষেত্র (যেমন: ভারী শিল্প কারখানা, মেগা কনস্ট্রাকশন সাইট, খনি ও কেমিক্যাল প্ল্যান্ট)-এ "
        "কর্মরত কর্মীদের সার্বক্ষণিক নিরাপত্তা ও জীবন রক্ষার্থে তৈরি একটি সেন্ট্রালাইজড আইওটি (IoT) কমান্ড সেন্টার। "
        "স্মার্ট সেফটি ভেস্টে সংযুক্ত বহুমুখী সেন্সর এবং হাই-প্রিসিশন জিপিএস (GPS) থেকে সরাসরি টেলিমেট্রি সংগ্রহ করে "
        "অ্যাপ্লিকেশনটি সুপারভাইজারকে রিয়েল-টাইম তথ্য, বিপদ সংকেত, ডেঞ্জার জোন জিওফেন্সিং এবং অফলাইন উদ্ধার সহায়তা প্রদান করে।"
    )
    story.append(Paragraph(exec_summary, body_style))
    story.append(Spacer(1, 8))

    # Core Features
    story.append(Paragraph("২. মূল ফিচারসমূহ ও টেকনিক্যাল স্পেসিফিকেশন (Core Features)", h1_style))

    # Feature 1
    story.append(Paragraph("ক. রিয়েল-টাইম সেন্সর মনিটরিং ও হেলথ টেলিমেট্রি (Live Telemetry)", h2_style))
    story.append(Paragraph("• <b>ফল ডিটেকশন (Fall & Motion Detection):</b> অ্যাক্সেলেরোমিটার ও জাইরোস্কোপের সাহায্যে কর্মী হোঁচট খেলে বা উঁচু থেকে পড়ে গেলে তৎক্ষণাৎ স্বয়ংক্রিয় Fall Alert প্রদর্শন।", bullet_style))
    story.append(Paragraph("• <b>মোশন স্টেট অ্যানালাইসিস:</b> কর্মীর শারীরিক অবস্থান (Walking, Running, Idle) লাইভ ট্র্যাকিং।", bullet_style))
    story.append(Paragraph("• <b>পরিবেশগত ঝুঁকি ও গ্যাস শনাক্তকরণ:</b> কর্মক্ষেত্রের তাপমাত্রা, আর্দ্রতা এবং বিষাক্ত ধোঁয়া/গ্যাসের মাত্রা অতিরিক্ত বৃদ্ধি পেলে ক্রিটিক্যাল অ্যালার্ম।", bullet_style))
    story.append(Paragraph("• <b>ইমার্জেন্সি এসওএস (SOS Alert):</b> ভেস্টের এসওএস বোতাম প্রেস করলে তাৎক্ষণিক অডিও-ভিজ্যুয়াল ইমার্জেন্সি সাইরেন।", bullet_style))
    story.append(Spacer(1, 4))

    # Feature 2
    story.append(Paragraph("খ. ইন্টারেক্টিভ জিপিএস ও পরিচ্ছন্ন ম্যাপ ইন্টারফেস (GPS & Clean Map UI)", h2_style))
    story.append(Paragraph("• <b>হাই-অ্যাকিউরেসি লাইভ পজিশনিং:</b> গুগল ম্যাপে কর্মীর বর্তমান অবস্থানের লাইভ মুভমেন্ট ট্র্যাকিং।", bullet_style))
    story.append(Paragraph("• <b>ডুয়াল ম্যাপ ভিউ:</b> এক ট্যাপে সাধারণ স্ট্রিট ভিউ এবং স্যাটেলাইট/হাইব্রিড ভিউ পরিবর্তন।", bullet_style))
    story.append(Paragraph("• <b>ইউনিফাইড ফ্লোটিং টুলবার:</b> ম্যাপের স্ক্রিন খোলামেলা রাখতে সকল কন্ট্রোল (ভয়েস অ্যালার্ট, ট্রেইল, জোন ম্যানেজার, ক্যামেরা সেন্টার) একটি স্লিম ফ্লোটিং বারে বিন্যস্ত।", bullet_style))
    story.append(Paragraph("• <b>কোলাপ্সিবল বটম HUD:</b> নিচের টেলিমেট্রি প্যানেলটি ডিফল্টভাবে মিনিমালিস্ট (৫৪ dp) অবস্থায় থাকে যাতে পুরো ম্যাপ স্পষ্টভাবে দেখা যায়; প্রয়োজন হলে ট্যাপ করে সম্পূর্ণ কো-অর্ডিনেট প্যানেল এক্সপ্যান্ড করা যায়।", bullet_style))
    story.append(Spacer(1, 4))

    # Feature 3
    story.append(Paragraph("গ. ডেঞ্জার জোন অটো-অ্যালার্ট ও জিওফেন্সিং (Geofencing & Voice Warning)", h2_style))
    story.append(Paragraph("• <b>কাস্টম ডেঞ্জার জোন তৈরি:</b> ম্যাপে যেকোনো স্থানে লং-প্রেস করে বা কো-অর্ডিনেট দিয়ে রেড জোন (হাই ভোল্টেজ, এক্সকাভেশন, কেমিক্যাল হ্যাজার্ড) নির্ধারণ।", bullet_style))
    story.append(Paragraph("• <b>প্রক্সিমিটি ডিটেকশন (Proximity Alert):</b> কর্মী নিষিদ্ধ এলাকার ৫০ মিটারের কাছে এলে দূরত্বসহ সতর্কবার্তা প্রদর্শন।", bullet_style))
    story.append(Paragraph("• <b>ভয়েস ওয়ার্নিং (TTS Voice Alert):</b> ডেঞ্জার জোনে ঢুকলে বা কাছাকাছি এলে স্বয়ংক্রিয় ভয়েস ওয়ার্নিং (\"Warning! Approaching restricted hazard area\")।", bullet_style))
    story.append(Paragraph("• <b>কালার-কোডেড ভিজ্যুয়াল জোন:</b> নিরাপদ (সবুজ), সন্নিকটবর্তী (হলুদ/কমলা) এবং সীমানা লঙ্ঘন (ব্লিঙ্কিং রেড) দ্বারা তাৎক্ষণিক স্ট্যাটাস।", bullet_style))
    story.append(Spacer(1, 4))

    # Feature 4
    story.append(Paragraph("ঘ. লাস্ট নোন লোকেশন ও অফলাইন রেসকিউ (Last Known Location & Beacon)", h2_style))
    story.append(Paragraph("• <b>সিগন্যাল লস অটো-ক্যাশ:</b> কোনো কর্মী টানেল বা বেসমেন্টে সিগন্যাল হারালে তার সর্বশেষ সঠিক অবস্থান ও সময় স্বয়ংক্রিয়ভাবে সংরক্ষিত থাকে।", bullet_style))
    story.append(Paragraph("• <b>টাইম-ল্যাপ্স ট্র্যাকার:</b> সংযোগ বিচ্ছিন্ন হওয়ার পর কত মিনিট পার হয়েছে (উদা: 'Lost 5m ago') তা প্রদর্শন।", bullet_style))
    story.append(Paragraph("• <b>১-ক্লিক রেসকিউ অ্যাকশন:</b> উদ্ধারকারী দলের জন্য 'COPY GPS' এবং এক ট্যাপে গুগল ম্যাপস নেভিগেশন চালু করার 'NAVIGATE' বাটন।", bullet_style))
    story.append(Spacer(1, 4))

    # Feature 5
    story.append(Paragraph("ঙ. মুভমেন্ট ব্রেডক্রাম্বস ট্রেইল (Beacon Breadcrumbs Trail)", h2_style))
    story.append(Paragraph("• <b>মুভমেন্ট ফুটপ্রিন্ট:</b> শিফটে কর্মী কোন কোন রুট দিয়ে হেঁটেছেন তার হিস্টোরিক্যাল ট্রেইল লাইন ম্যাপের ওপর অঙ্কন।", bullet_style))
    story.append(Paragraph("• <b>ট্রেইল টগল ও ক্লিয়ার:</b> প্রয়োজন অনুসারে ট্রেইল অফ/অন করা এবং সিঙ্গেল ট্যাপে ট্রেইল হিস্টোরি ক্লিয়ার করার সুবিধা।", bullet_style))
    story.append(Spacer(1, 10))

    # Summary Table for Quick Glance
    story.append(Paragraph("৩. ফিচার সামারি মেট্রিক্স (Supervisor Quick Matrix)", h1_style))
    
    table_data = [
        [
            Paragraph("মডিউল (Module)", table_header_style),
            Paragraph("প্রধান কাজ (Functionality)", table_header_style),
            Paragraph("সুবিধা (Business & Safety Impact)", table_header_style)
        ],
        [
            Paragraph("<b>Telemetry & Fall</b>", table_cell_style),
            Paragraph("অ্যাক্সেলোমিটার, গ্যাস, তাপমাত্রা, এসওএস বাটন", table_cell_style),
            Paragraph("দুর্ঘটনা ও শারীরিক আঘাতের তাৎক্ষণিক শনাক্তকরণ", table_cell_style)
        ],
        [
            Paragraph("<b>Geofencing</b>", table_cell_style),
            Paragraph("ডেঞ্জার জোন, প্রক্সিমিটি ও স্বয়ংক্রিয় ভয়েস ওয়ার্নিং", table_cell_style),
            Paragraph("অনুমতিহীন ও বিপজ্জনক এলাকায় প্রবেশ প্রতিরোধ", table_cell_style)
        ],
        [
            Paragraph("<b>Map UI & HUD</b>", table_cell_style),
            Paragraph("স্যাটেলাইট/স্ট্রিট ভিউ, স্লিম ডক, কোলাপ্সিবল HUD", table_cell_style),
            Paragraph("ম্যাপের সর্বোচ্চ দৃশ্যমানতা ও আধুনিক ইউজার অভিজ্ঞতা", table_cell_style)
        ],
        [
            Paragraph("<b>Last Known Location</b>", table_cell_style),
            Paragraph("অফলাইন ক্যাশিং, টাইম-ল্যাপ্স, ১-ক্লিক নেভিগেশন", table_cell_style),
            Paragraph("জরুরি অবস্থায় দ্রুততম সময়ে রেসকিউ অপারেশন", table_cell_style)
        ],
        [
            Paragraph("<b>Breadcrumbs</b>", table_cell_style),
            Paragraph("হিস্টোরিক্যাল ফুটপ্রিন্ট ও রুট ট্র্যাকিং ট্রেইল", table_cell_style),
            Paragraph("কর্মী উপস্থিতি ও কাজের আওতাধীন এলাকা যাচাই", table_cell_style)
        ]
    ]

    col_widths = [100, 210, 194]
    t = Table(table_data, colWidths=col_widths, repeatRows=1)
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor("#0F172A")),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('ALIGN', (0, 0), (-1, -1), 'LEFT'),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('TOPPADDING', (0, 0), (-1, -1), 5),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 5),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
        ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#CBD5E1")),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
    ]))
    story.append(t)
    story.append(Spacer(1, 14))

    # Supervisor Quick Presentation Points Box
    story.append(Paragraph("৪. সুপারভাইজারের সামনে উপস্থাপনের প্রধান ৫টি পয়েন্ট (Executive Pitch)", h1_style))
    story.append(Paragraph("১. <b>রিয়েল-টাইম জীবন রক্ষা:</b> কর্মীর মাটিতে পড়ে যাওয়া (Fall Detection), অতিরিক্ত তাপমাত্রা বা বিষাক্ত গ্যাস লিক হলে সেকেন্ডের মধ্যে অ্যালার্ট।", bullet_style))
    story.append(Paragraph("২. <b>স্বয়ংক্রিয় জিওফেন্সিং ও ভয়েস ওয়ার্নিং:</b> নিষিদ্ধ ডেঞ্জার জোনে যাওয়ার আগেই মুখে সতর্কবার্তা (Voice Warning) দিয়ে দুর্ঘটনা প্রতিহত করা।", bullet_style))
    story.append(Paragraph("৩. <b>অফলাইন ব্যাকআপ ও ১-ক্লিক উদ্ধার:</b> সিগন্যাল চলে গেলেও লাস্ট নোন লোকেশন প্রদর্শন এবং তাৎক্ষণিক গুগল ম্যাপস উদ্ধার রুট।", bullet_style))
    story.append(Paragraph("৪. <b>মুভমেন্ট ফুটপ্রিন্ট ট্র্যাকিং:</b> শিফটে কর্মী কোন কোন পথ দিয়ে হেঁটেছেন তা ব্রেডক্রাম্বস ট্রেইলের মাধ্যমে নিশ্চিত করা।", bullet_style))
    story.append(Paragraph("৫. <b>অপ্টিমাইজড ক্লিন ম্যাপ ডিজাইন:</b> অপ্রয়োজনীয় জটলা সরিয়ে সম্পূর্ণ ম্যাপ দৃশ্যমান রেখে এক নজরে সার্বিক সাইট কন্ট্রোল।", bullet_style))

    doc.build(story, canvasmaker=NumberedCanvas)
    print(f"Successfully generated {filename}")

if __name__ == "__main__":
    build_pdf()
