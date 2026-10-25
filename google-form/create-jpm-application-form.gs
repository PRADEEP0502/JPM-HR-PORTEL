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
 * The question titles below match what the JPM HRMS dashboard sync recognises automatically.
 * If you rename a question, either keep the meaning (e.g. "Mobile Number") or set a column
 * mapping override in the dashboard (Settings → Google Sheets integration → Column mapping).
 */

// ---------------------------------------------------------------------------------------------
// EDIT THESE IF NEEDED. Departments must match the departments configured in the dashboard
// (Departments page) exactly - otherwise candidates are imported as "Unassigned".
// ---------------------------------------------------------------------------------------------
var COMPANY = 'JPM – Junior Processing Mill';
// 10-digit Indian mobile. Also accepts the +91 / 91 / 0 prefixes and one space or dash in the
// middle (e.g. "98765 43210") - the same numbers the dashboard accepts and normalises.
var MOBILE_PATTERN = '^(\\+?91[ -]?|0)?[6-9][0-9]{4}[ -]?[0-9]{5}$';

var DEPARTMENTS = [
  'Customer Support',
  'Design',
  'Engineering',
  'Finance',
  'Human Resources',
  'Operations',
  'Sales & Marketing'
];

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
      .setChoiceValues(DEPARTMENTS)
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
      .setChoiceValues(['I confirm that the information given is true, and I consent to ' + COMPANY +
                        ' using it for recruitment purposes.'])
      .setRequired(true);

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
