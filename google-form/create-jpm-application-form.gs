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

// Response sheet of the form that is already live (used only by updateExistingForm).
var RESPONSE_SPREADSHEET_ID = '1vzyLJXwbTzchDVyYegagKZwCHKCX_WJQ-1NUW2ThUA4';

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

function findItem_(form, title, type) {
  return form.getItems(type).filter(function (item) { return item.getTitle() === title; })[0] || null;
}
