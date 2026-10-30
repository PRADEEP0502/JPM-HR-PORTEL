/**
 * JPM – Junior Processing Mill : Candidate Application Form generator (Google Apps Script)
 *
 * HOW TO USE
 *   1. Sign in to the Google account that should own the form (e.g. the HR account).
 *   2. Open https://script.google.com  →  New project.
 *   3. Delete the sample code, paste this whole file, click Save.
 *   4. Select the function  createJpmApplicationForm  and click  Run.
 *      Approve the permission prompt (it needs to create a Form and a Sheet in YOUR Drive).
 *   5. The "Execution log" panel opens at the bottom - copy the links and the Spreadsheet ID printed there.
 *      Run it ONCE: every run creates a new form and a new response sheet.
 *   6. Add the three file-upload questions by hand (Apps Script cannot create them) - see the log.
 *
 * ALREADY HAVE THE FORM?  Don't create a new one. Set RESPONSE_SPREADSHEET_ID below, then run
 *   updateExistingForm  - it (re)builds the Department → Sub-work sections and the declaration text
 *   in your current form. Safe to run again whenever DEPARTMENTS changes; answers are not touched.
 *
 * The question titles below match what the JPM HRMS dashboard sync recognises automatically.
 * If you rename a question, either keep the meaning (e.g. "Mobile Number") or set a column
 * mapping override in the dashboard (Settings → Google Sheets integration → Column mapping).
 */

// ---------------------------------------------------------------------------------------------
// EDIT THESE IF NEEDED. Departments and sub-work must match the dashboard's Departments page
// exactly - otherwise candidates are imported as "Unassigned".
// ---------------------------------------------------------------------------------------------
var COMPANY = 'JPM – Junior Processing Mill';
// 10-digit Indian mobile. Also accepts the +91 / 91 / 0 prefixes and one space or dash in the
// middle (e.g. "98765 43210") - the same numbers the dashboard accepts and normalises.
var MOBILE_PATTERN = '^(\\+?91[ -]?|0)?[6-9][0-9]{4}[ -]?[0-9]{5}$';

// Department → its sub-work. The candidate picks a department, then sees only that department's sub-work.
var DEPARTMENTS = {
  'Production': ['Machine Operator', 'Helper', 'Supervisor'],
  'Quality Control': ['QC Inspector', 'Lab Assistant'],
  'Maintenance': ['Electrician'],
  'Office': ['Accountant', 'Data Entry', 'Administrative Officer', 'Receptionist', 'HR']
};

// Response sheet of the form that is already live (used by updateExistingForm and the auto-send below).
var RESPONSE_SPREADSHEET_ID = '1QlF0mX1fMO2DWJjj1RG9noQiu2dYHRYCJXUJx9hDaAw';

// Auto-send to the dashboard: copy FORM_KEY from the dashboard, Settings → Google Sheets integration →
// "Google Form → dashboard (instant)", then run installAutoSend once. DASHBOARD_URL is the backend (Render).
var DASHBOARD_URL = 'https://jpm-hr-portel.onrender.com/api/public/form-submissions';
var FORM_KEY = 'PASTE-THE-FORM-KEY-HERE';
var DASHBOARD_TIMEZONE = 'Asia/Kolkata';
var MAX_FILE_BYTES_PER_APPLICATION = 20 * 1024 * 1024;

var SUBWORK_SECTION_PREFIX = 'Sub-work – ';
var DECLARATION = 'I confirm that the information given is true, and I consent to ' + COMPANY +
                  ' using it, including any Aadhaar copy I upload, for recruitment and identity verification only.';

function createJpmApplicationForm() {
  var form = FormApp.create('JPM – Job Application Form');
  form.setTitle('Job Application – ' + COMPANY)
      .setDescription(
        'Thank you for your interest in joining ' + COMPANY + '.\n\n' +
        'Please fill in your details carefully. Fields marked * are required. ' +
        'Your information is used only for recruitment and is kept confidential by the JPM HR Department.')
      .setConfirmationMessage(
        'Thank you! Your application has been submitted to ' + COMPANY + '. ' +
        'Our HR team will contact you if your profile is shortlisted.')
      .setAllowResponseEdits(false)
      .setShowLinkToRespondAgain(false)
      .setProgressBar(true);

  // ---------------- Section 1: Personal details ----------------
  form.addSectionHeaderItem().setTitle('Personal Details');

  form.addTextItem().setTitle('Full Name').setHelpText('As per your certificates').setRequired(true);

  form.addDateItem().setTitle('Date of Birth').setRequired(true);

  form.addTextItem()
      .setTitle('Mobile Number')
      .setHelpText('10-digit mobile number (WhatsApp preferred), e.g. 9876543210')
      .setRequired(true)
      .setValidation(FormApp.createTextValidation()
          .requireTextMatchesPattern(MOBILE_PATTERN)
          .setHelpText('Enter a valid 10-digit Indian mobile number')
          .build());

  form.addTextItem()
      .setTitle('Email')
      .setHelpText('We will send interview details to this address')
      .setRequired(true)
      .setValidation(FormApp.createTextValidation()
          .requireTextIsEmail()
          .setHelpText('Enter a valid email address')
          .build());

  form.addParagraphTextItem().setTitle('Address').setHelpText('Current address with city and PIN code').setRequired(true);

  // ---------------- Section 2: Education ----------------
  form.addPageBreakItem().setTitle('Educational Details');

  form.addTextItem().setTitle('Highest Qualification').setHelpText('e.g. B.E. Mechanical, B.Com, Diploma').setRequired(true);
  form.addTextItem().setTitle('Institution').setHelpText('College / University name').setRequired(true);
  form.addTextItem()
      .setTitle('Year of Passing')
      .setRequired(true)
      .setValidation(FormApp.createTextValidation()
          .requireTextMatchesPattern('^(19|20)[0-9]{2}$')
          .setHelpText('Enter a 4-digit year, e.g. 2022')
          .build());

  // ---------------- Section 3: Employment ----------------
  form.addPageBreakItem().setTitle('Employment Details').setHelpText('Freshers can write "Fresher" or 0 for experience and leave company fields blank.');

  form.addTextItem()
      .setTitle('Total Experience (in years)')
      .setHelpText('e.g. 0, 1.5, 3 or "Fresher"')
      .setRequired(true);
  form.addTextItem().setTitle('Previous Company');
  form.addTextItem().setTitle('Previous Designation');
  form.addParagraphTextItem().setTitle('Key Skills').setHelpText('Separate skills with commas, e.g. Tally, Excel, Machine operation').setRequired(true);
  form.addTextItem().setTitle('Expected Salary').setHelpText('e.g. ₹25,000 per month or ₹3 LPA');

  // ---------------- Section 4: Application ----------------
  form.addPageBreakItem().setTitle('Application');

  form.addListItem()
      .setTitle('Department')
      .setHelpText('Department you are applying for')
      .setChoiceValues(Object.keys(DEPARTMENTS))
      .setRequired(true);

  // ---------------- Section 5: References ----------------
  form.addPageBreakItem().setTitle('References');

  form.addSectionHeaderItem().setTitle('Professional Reference').setHelpText('A previous manager, teacher or colleague (optional for freshers)');
  form.addTextItem().setTitle('Reference Name');
  form.addTextItem().setTitle('Reference Relationship / Designation').setHelpText('e.g. Former Manager, HOD');
  form.addTextItem().setTitle('Reference Contact Number');

  form.addSectionHeaderItem().setTitle('Family Reference').setHelpText('A family member we can contact');
  form.addTextItem().setTitle('Family Reference Name').setRequired(true);
  form.addListItem()
      .setTitle('Relationship with Family Member')
      .setChoiceValues(['Father', 'Mother', 'Spouse', 'Brother', 'Sister', 'Son', 'Daughter', 'Guardian'])
      .setRequired(true);
  form.addTextItem()
      .setTitle('Family Reference Contact Number')
      .setRequired(true)
      .setValidation(FormApp.createTextValidation()
          .requireTextMatchesPattern(MOBILE_PATTERN)
          .setHelpText('Enter a valid 10-digit Indian mobile number')
          .build());

  // ---------------- Section 6: Declaration ----------------
  form.addPageBreakItem().setTitle('Documents & Declaration')
      .setHelpText('Please upload the requested documents (if any) and confirm the declaration below.');

  form.addCheckboxItem()
      .setTitle('Declaration')
      .setChoiceValues([DECLARATION])
      .setRequired(true);

  setUpDepartmentSections_(form);

  // ---------------- Response sheet ----------------
  var created = SpreadsheetApp.create('JPM – Job Applications (Responses)');
  var ssId = created.getId();
  form.setDestination(FormApp.DestinationType.SPREADSHEET, ssId);

  // Google adds the "Form Responses 1" tab asynchronously. Re-open the spreadsheet (the object
  // created above can be stale) and wait up to ~20 s for the linked tab to appear.
  var ss = null;
  var responseSheet = null;
  for (var attempt = 0; attempt < 10 && !responseSheet; attempt++) {
    Utilities.sleep(2000);
    SpreadsheetApp.flush();
    ss = SpreadsheetApp.openById(ssId);
    responseSheet = ss.getSheets().filter(function (sh) {
      return !!sh.getFormUrl();
    })[0] || null;
  }

  // Remove the empty default tab ("Sheet1") so only the response tab remains.
  if (responseSheet) {
    ss.getSheets().forEach(function (sh) {
      if (sh.getSheetId() !== responseSheet.getSheetId() && sh.getLastRow() === 0 && ss.getSheets().length > 1) {
        ss.deleteSheet(sh);
      }
    });
  }

  var lines = [
    '============================================================',
    'JPM application form created',
    '============================================================',
    'Form - share this link with candidates : ' + form.getPublishedUrl(),
    'Form - edit (HR only)                  : ' + form.getEditUrl(),
    'Response sheet (keep PRIVATE)          : ' + ss.getUrl(),
    '',
    'For the JPM HRMS dashboard (Settings → Google Sheets integration):',
    '  Spreadsheet ID : ' + ss.getId(),
    '  Sheet / range  : ' + (responseSheet ? responseSheet.getName()
        : 'Form Responses 1   (the response tab was not visible yet - open the sheet and check the tab name)'),
    '  Form URL       : ' + form.getPublishedUrl(),
    '',
    'NEXT (manual, about 2 minutes) - add file upload questions on the last page,',
    '"Documents & Declaration", using  + → File upload :',
    '  1. "Upload Resume"            required, PDF/Document, max 10 MB',
    '  2. "Aadhaar Card Upload"      optional, PDF/Image, max 10 MB  (ask for MASKED Aadhaar)',
    '  3. "Educational Certificates" optional, PDF/Image, up to 5 files',
    '  Note: file upload makes candidates sign in with a Google account.',
    '',
    'Then share the response sheet (Viewer) with the service-account email shown in the dashboard.',
    'Do NOT make the sheet public.'
  ];
  Logger.log(lines.join('\n'));
  return { formUrl: form.getPublishedUrl(), editUrl: form.getEditUrl(), spreadsheetId: ss.getId() };
}

/** Adds the Department → Sub-work sections and the Aadhaar consent declaration to the form that is already live. */
function updateExistingForm() {
  var formUrl = SpreadsheetApp.openById(RESPONSE_SPREADSHEET_ID).getFormUrl();
  if (!formUrl) throw new Error('This spreadsheet is not linked to a form. Check RESPONSE_SPREADSHEET_ID.');
  var form = FormApp.openByUrl(formUrl);
  setUpDepartmentSections_(form);

  var declaration = findItem_(form, 'Declaration', FormApp.ItemType.CHECKBOX);
  if (declaration) declaration.asCheckboxItem().setChoiceValues([DECLARATION]);

  Logger.log('Form updated: ' + Object.keys(DEPARTMENTS).length + ' departments with sub-work. ' +
             'Check it here: ' + form.getEditUrl());
}

/**
 * One section per department, right after the "Department" question, holding a "Position (<Department>)"
 * question with that department's sub-work. Choosing a department jumps to its section, and every section
 * continues to "References". Sections from an earlier run are replaced.
 */
function setUpDepartmentSections_(form) {
  form.getItems().forEach(function (item) {
    var title = item.getTitle();
    var isOldSection = item.getType() === FormApp.ItemType.PAGE_BREAK && title.indexOf(SUBWORK_SECTION_PREFIX) === 0;
    var isOldQuestion = item.getType() === FormApp.ItemType.LIST && /^Position \(/.test(title);
    if (isOldSection || isOldQuestion) form.deleteItem(item);
  });

  var department = findItem_(form, 'Department', FormApp.ItemType.LIST);
  var references = findItem_(form, 'References', FormApp.ItemType.PAGE_BREAK);
  if (!department || !references) throw new Error('The form needs a "Department" dropdown and a "References" section.');

  var list = department.asListItem();
  var insertAt = department.getIndex() + 1;
  var choices = Object.keys(DEPARTMENTS).map(function (name) {
    var section = form.addPageBreakItem()
        .setTitle(SUBWORK_SECTION_PREFIX + name)
        .setHelpText('Choose the work you are applying for in ' + name + '.')
        .setGoToPage(references.asPageBreakItem());
    form.moveItem(section.getIndex(), insertAt++);
    var question = form.addListItem()
        .setTitle('Position (' + name + ')')
        .setChoiceValues(DEPARTMENTS[name])
        .setRequired(true);
    form.moveItem(question.getIndex(), insertAt++);
    return list.createChoice(name, section);
  });
  list.setChoices(choices);
}

/**
 * Submits 5 sample applications to the live form (one per department, with sub-work) so the dashboard
 * sync has something to import. Names end in "(Demo)" and emails use example.com - archive them when done.
 * Run updateExistingForm first so the "Position (<Department>)" questions exist.
 */
function addDemoCandidates() {
  var form = FormApp.openByUrl(SpreadsheetApp.openById(RESPONSE_SPREADSHEET_ID).getFormUrl());
  var demos = [
    {name: 'Karthik Raja (Demo)', dob: '1998-04-12', mobile: '9000000001', dept: 'Production', position: 'Machine Operator',
     qual: 'Diploma in Mechanical Eng.', inst: 'PSG Polytechnic College', year: '2018', exp: '5', company: 'TVS Mills',
     designation: 'Machine Operator', skills: 'Spinning machines, Preventive maintenance, Safety', salary: '₹22,000 per month'},
    {name: 'Meena Lakshmi (Demo)', dob: '2001-09-03', mobile: '9000000002', dept: 'Quality Control', position: 'QC Inspector',
     qual: 'B.Sc Textile Technology', inst: 'Bharathiar University', year: '2022', exp: '2', company: 'KPR Mill',
     designation: 'QC Trainee', skills: 'Yarn testing, GSM checks, Documentation', salary: '₹20,000 per month'},
    {name: 'Suresh Kumar (Demo)', dob: '1995-01-25', mobile: '9000000003', dept: 'Maintenance', position: 'Electrician',
     qual: 'ITI Electrician', inst: 'Government ITI Coimbatore', year: '2014', exp: '8', company: 'Lakshmi Machine Works',
     designation: 'Electrician', skills: 'Motor rewinding, PLC basics, Panel wiring', salary: '₹28,000 per month'},
    {name: 'Divya Priya (Demo)', dob: '2000-06-17', mobile: '9000000004', dept: 'Office', position: 'Accountant',
     qual: 'B.Com', inst: 'Madras Christian College', year: '2021', exp: '3', company: 'Ramesh & Co',
     designation: 'Accounts Assistant', skills: 'Tally, GST, Excel', salary: '₹25,000 per month'},
    {name: 'Vignesh Murugan (Demo)', dob: '2004-11-08', mobile: '9000000005', dept: 'Production', position: 'Helper',
     qual: '12th Standard', inst: 'Government Higher Secondary School, Tiruppur', year: '2022', exp: 'Fresher', company: '',
     designation: '', skills: 'Material handling, Team work', salary: '₹15,000 per month'}
  ];

  demos.forEach(function (d, i) {
    var answers = {
      'Full Name': d.name, 'Date of Birth': new Date(d.dob + 'T00:00:00'), 'Mobile Number': d.mobile,
      'Email': 'demo.candidate' + (i + 1) + '@example.com', 'Address': (i + 1) + ', Main Road, Coimbatore 641001',
      'Highest Qualification': d.qual, 'Institution': d.inst, 'Year of Passing': d.year,
      'Total Experience (in years)': d.exp, 'Previous Company': d.company, 'Previous Designation': d.designation,
      'Key Skills': d.skills, 'Expected Salary': d.salary, 'Department': d.dept,
      'Reference Name': d.company ? 'Ramesh Kumar' : '', 'Reference Relationship / Designation': d.company ? 'Former Manager' : '',
      'Reference Contact Number': d.company ? '9000000100' : '',
      'Family Reference Name': 'Murugesan', 'Relationship with Family Member': 'Father', 'Family Reference Contact Number': '900000020' + (i + 1)
    };
    answers['Position (' + d.dept + ')'] = d.position;

    var response = form.createResponse();
    form.getItems().forEach(function (item) {
      var title = item.getTitle();
      switch (item.getType()) {
        case FormApp.ItemType.TEXT:
          if (answers[title]) response.withItemResponse(item.asTextItem().createResponse(answers[title]));
          break;
        case FormApp.ItemType.PARAGRAPH_TEXT:
          if (answers[title]) response.withItemResponse(item.asParagraphTextItem().createResponse(answers[title]));
          break;
        case FormApp.ItemType.DATE:
          if (answers[title]) response.withItemResponse(item.asDateItem().createResponse(answers[title]));
          break;
        case FormApp.ItemType.LIST:
          if (answers[title]) response.withItemResponse(item.asListItem().createResponse(answers[title]));
          break;
        case FormApp.ItemType.CHECKBOX:
          if (title === 'Declaration') {
            var cb = item.asCheckboxItem();
            response.withItemResponse(cb.createResponse([cb.getChoices()[0].getValue()]));
          }
          break;
      }
    });
    response.submit();
  });
  Logger.log('Submitted ' + demos.length + ' demo applications. In the dashboard: Settings → Google Sheets integration → Sync now.');
}

// ---------------------------------------------------------------------------------------------
// AUTO-SEND: every new application goes to the dashboard as soon as the candidate submits.
// ---------------------------------------------------------------------------------------------

/**
 * Run ONCE after pasting FORM_KEY. Adds two triggers: one on every form submit, and an hourly one that
 * retries anything the dashboard could not take (for example while the free server was asleep).
 * Only applications submitted from now on are sent; run sendExistingApplications to send older ones too.
 */
function installAutoSend() {
  if (FORM_KEY.indexOf('PASTE') === 0) throw new Error('Paste FORM_KEY from the dashboard (Settings → Google Sheets integration) first.');
  ScriptApp.getProjectTriggers().forEach(function (t) {
    var fn = t.getHandlerFunction();
    if (fn === 'onApplicationSubmitted' || fn === 'sendPendingApplications') ScriptApp.deleteTrigger(t);
  });
  ScriptApp.newTrigger('onApplicationSubmitted').forSpreadsheet(RESPONSE_SPREADSHEET_ID).onFormSubmit().create();
  ScriptApp.newTrigger('sendPendingApplications').timeBased().everyHours(1).create();

  var props = PropertiesService.getScriptProperties();
  if (!props.getProperty('JPM_LAST_ROW_SENT')) props.setProperty('JPM_LAST_ROW_SENT', String(responseSheet_().getLastRow()));
  checkDashboard_();

  var ss = SpreadsheetApp.openById(RESPONSE_SPREADSHEET_ID);
  Logger.log('Auto-send is on. New applications appear in the dashboard within a minute.\n' +
             'Share this form link with candidates: ' + FormApp.openByUrl(ss.getFormUrl()).getPublishedUrl());
}

/** Sends every application already in the response sheet (duplicates and earlier imports are skipped by the dashboard). */
function sendExistingApplications() {
  PropertiesService.getScriptProperties().setProperty('JPM_LAST_ROW_SENT', '1');
  Logger.log(sendPendingApplications() + ' application(s) sent.');
}

function onApplicationSubmitted() {
  sendPendingApplications();
}

/** Sends the rows after the last one the dashboard accepted, in order, stopping at the first failure. */
function sendPendingApplications() {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(120000)) return 0;
  try {
    var props = PropertiesService.getScriptProperties();
    var sheet = responseSheet_();
    var tz = sheet.getParent().getSpreadsheetTimeZone();
    var lastCol = sheet.getLastColumn();
    var headers = sheet.getRange(1, 1, 1, lastCol).getDisplayValues()[0];
    var sent = 0;
    for (var row = Number(props.getProperty('JPM_LAST_ROW_SENT') || 1) + 1; row <= sheet.getLastRow(); row++) {
      var values = sheet.getRange(row, 1, 1, lastCol).getValues()[0];
      var result = sendRow_(headers, values, tz, row);
      if (!result) break; // dashboard not reachable: the hourly run tries again
      props.setProperty('JPM_LAST_ROW_SENT', String(row));
      sent++;
      Logger.log('Row ' + row + ': ' + result.status + (result.candidateCode ? ' (' + result.candidateCode + ')' : '') +
                 (result.messages && result.messages.length ? ' - ' + result.messages.join('; ') : ''));
    }
    return sent;
  } finally {
    lock.releaseLock();
  }
}

function sendRow_(headers, values, tz, row) {
  var payload = {
    key: FORM_KEY,
    headers: headers,
    values: values.map(function (v, i) {
      if (!(v instanceof Date)) return v === null || v === undefined ? '' : String(v);
      return headers[i] === 'Timestamp' ? Utilities.formatDate(v, DASHBOARD_TIMEZONE, 'yyyy-MM-dd HH:mm:ss')
                                        : Utilities.formatDate(v, tz, 'yyyy-MM-dd');
    }),
    files: uploadedFiles_(headers, values, row)
  };
  for (var attempt = 1; attempt <= 3; attempt++) {
    try {
      var res = UrlFetchApp.fetch(DASHBOARD_URL, {
        method: 'post', contentType: 'application/json', payload: JSON.stringify(payload), muteHttpExceptions: true
      });
      var code = res.getResponseCode();
      if (code === 200) return JSON.parse(res.getContentText());
      if (code === 400 || code === 401 || code === 413) {
        throw new Error('Dashboard refused row ' + row + ' (HTTP ' + code + '): ' + res.getContentText().slice(0, 300));
      }
      Logger.log('Row ' + row + ': dashboard answered HTTP ' + code + ', retrying (the free server may be waking up)');
    } catch (e) {
      if (String(e.message).indexOf('Dashboard refused') === 0) throw e;
      Logger.log('Row ' + row + ': ' + e.message + ', retrying');
    }
    if (attempt < 3) Utilities.sleep(20000);
  }
  return null;
}

/** Resume, Aadhaar and certificates uploaded in the form, read from Drive and sent along. */
function uploadedFiles_(headers, values, row) {
  var files = [];
  var total = 0;
  headers.forEach(function (header, i) {
    var h = header.toLowerCase();
    var field = h.indexOf('resume') >= 0 ? 'RESUME' : h.indexOf('aadhaar') >= 0 ? 'AADHAAR'
              : h.indexOf('certificate') >= 0 ? 'CERTIFICATE' : null;
    if (!field || !values[i]) return;
    var ids = String(values[i]).match(/(?:[?&]id=|\/d\/)([-\w]{20,})/g) || [];
    ids.forEach(function (match) {
      var id = match.replace(/^(?:[?&]id=|\/d\/)/, '');
      try {
        var file = DriveApp.getFileById(id);
        if (total + file.getSize() > MAX_FILE_BYTES_PER_APPLICATION) {
          Logger.log('Row ' + row + ': ' + file.getName() + ' not sent (too large) - HR can upload it in the dashboard');
          return;
        }
        total += file.getSize();
        files.push({ field: field, name: file.getName(), data: Utilities.base64Encode(file.getBlob().getBytes()) });
      } catch (e) {
        Logger.log('Row ' + row + ': could not read uploaded file ' + id + ' (' + e.message + ')');
      }
    });
  });
  return files.slice(0, 10);
}

function checkDashboard_() {
  var res = UrlFetchApp.fetch(DASHBOARD_URL, {
    method: 'post', contentType: 'application/json', muteHttpExceptions: true,
    payload: JSON.stringify({ key: FORM_KEY, headers: ['Timestamp'], values: [''] })
  });
  if (res.getResponseCode() === 401) throw new Error('FORM_KEY is wrong. Copy it again from the dashboard.');
}

function responseSheet_() {
  var ss = SpreadsheetApp.openById(RESPONSE_SPREADSHEET_ID);
  return ss.getSheets().filter(function (sh) { return !!sh.getFormUrl(); })[0] || ss.getSheets()[0];
}

function findItem_(form, title, type) {
  return form.getItems(type).filter(function (item) { return item.getTitle() === title; })[0] || null;
}
