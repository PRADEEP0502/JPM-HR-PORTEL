# JPM Job Application – Google Form setup

This folder contains a script that **creates the candidate application form in your Google
account**. It also creates a private response Sheet linked to the form. The question titles match
what the JPM HRMS dashboard imports automatically.

## 1. Create the form (about 5 minutes)

1. Sign in to the Google account that should own the form (for example the HR mailbox).
2. Open **https://script.google.com** and click **New project**.
3. Delete the sample code, then paste the whole content of
   [`create-jpm-application-form.gs`](create-jpm-application-form.gs) and click **Save**.
4. Check the `DEPARTMENTS` list at the top. It must match the departments in the dashboard's
   **Departments** page exactly.
5. Choose the function **`createJpmApplicationForm`** and click **Run**. Run it **once**: every run
   creates another form and sheet. If you run it twice by mistake, delete the extra copies from Drive.
   - Google will ask for permission ("Review permissions" → choose your account → "Allow"). The
     script only creates one Form and one Sheet in your own Drive.
6. Open the **Execution log**. It prints:
   - **Form link**: share this with candidates.
   - **Edit link**: for HR only.
   - **Response sheet link** and **Spreadsheet ID**: you need these for the dashboard.

## 2. Add the file-upload questions (manual, about 2 minutes)

Google's script service cannot create file-upload questions, so add them in the form editor (edit
link) on the last page, **Documents & Declaration**, using **+ → File upload**:

| Question title (type exactly) | Required | Allowed types | Max |
|---|---|---|---|
| `Upload Resume` | Yes | PDF, Document | 1 file, 10 MB |
| `Aadhaar Card Upload` | No | PDF, Image | 1 file, 10 MB |
| `Educational Certificates` | No | PDF, Image | 5 files, 10 MB |

In the help text of the Aadhaar question, ask for a **masked Aadhaar** (only the last 4 digits
visible).

**Note:** with a file-upload question, candidates must sign in with a Google account to submit.
If you don't want that, skip the upload questions, and HR can upload documents from the candidate's
profile in the dashboard instead.

## 3. What the form asks

| Page | Questions |
|---|---|
| Personal Details | Full Name\*, Date of Birth\*, Mobile Number\* (validated 10-digit Indian mobile), Email\* (validated), Address\* |
| Educational Details | Highest Qualification\*, Institution\*, Year of Passing\* (validated year) |
| Employment Details | Total Experience (in years)\*, Previous Company, Previous Designation, Key Skills\*, Expected Salary |
| Application | Department\* (dropdown) |
| References | Professional: Reference Name, Reference Relationship / Designation, Reference Contact Number · Family\*: Family Reference Name, Relationship with Family Member, Family Reference Contact Number |
| Documents & Declaration | the 3 upload questions (step 2) + Declaration\* (consent checkbox) |

The form is titled **"Job Application – JPM – Junior Processing Mill"**, with a progress bar and a
thank-you message. Candidates cannot edit their answers after submitting.

## 4. Connect it to the dashboard

1. **Keep the response sheet private.** Share it (and, if you added uploads, the form's
   *"(File responses)"* folder in Drive) as **Viewer** only with the dashboard's service-account
   email, shown in *Settings → Google Sheets integration*.
2. In the dashboard, as admin, open **Settings → Google Sheets integration**:
   - **Spreadsheet ID**: from the execution log.
   - **Sheet / range**: `Form Responses 1` (or the tab name from the log).
   - **Public Google Form link**: the form link, so HR can share it.
   - Click **Save settings**, then **Test connection**. Every field should show a detected column;
     only "Declaration" is listed as ignored.
   - Click **Sync now**, then turn on **Automatic synchronization**.
3. Submit one test application yourself and confirm it appears under **Candidates**.

> The server needs `GOOGLE_SERVICE_ACCOUNT_FILE` configured. See the main
> [README](../README.md#connecting-google-forms--sheets) for creating the service account.

## If you change question titles later
The dashboard recognizes common variations (for example "Phone Number" for "Mobile Number"). For
anything else, set a **Column mapping override** in *Settings → Google Sheets integration*. Adding a
new department to the dropdown? Add it in the dashboard's Departments page too, with the exact same
spelling.
