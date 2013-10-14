#!/usr/bin/env python

import os, sys, re, requests

xnat_auth_base='/xnat/data/JSESSION'
xnat_project_base='/xnat/data/projects/'

def project_name_to_xnat_project_id(project_name):
  return re.sub('[ ,]', '_', project_name) # Replace spaces and commas with underscores

def patient_name_to_xnat_subject_id(patient_name):
  return re.sub('[\^ ,]', '_', patient_name) # Replace ^, spaces, and commas with underscores

def study_uid_to_xnat_experiment_id(study_uid):
  return study_uid.replace('.', '_') # XNAT does not like periods in its IDs

def login(xnat_base_url, user, password):
  xnat_auth_url = xnat_base_url + xnat_auth_base
  r = requests.post(xnat_auth_url, auth=(user, password))
  if r.status_code == requests.codes.ok:
    jsessionid = r.text
    return jsessionid
  else:
    print 'Error: log in to XNAT failed - status code =', r.status_code
    r.raise_for_status()

def logout(xnat_base_url, jsessionid):
  xnat_auth_url = xnat_base_url + xnat_auth_base
  cookies = dict(JSESSIONID=jsessionid)
  r = requests.delete(xnat_auth_url, cookies=cookies)
  if r.status_code != requests.codes.ok:
    print 'Warning: XNAT logout request failed - status code =', r.status_code
  
def create_project(xnat_base_url, jsessionid, project_name):
  xnat_project_id = project_name_to_xnat_project_id(project_name)
  xnat_project_url = xnat_base_url + xnat_project_base  
  payload = { 'ID': xnat_project_id, 'name': project_name } 
  cookies = dict(JSESSIONID=jsessionid)
  r = requests.post(xnat_project_url, params=payload, cookies=cookies)
  if r.status_code == requests.codes.ok: # XNAT returns 200 rather than 201 even if project does not already exist
    print 'Project', project_name, 'created'
  else:
    print 'Warning: failed to create project', project_name, '- status code =', r.status_code
    r.raise_for_status()

def create_subject(xnat_base_url, jsessionid, project_name, patient_name):
  xnat_project_id = project_name_to_xnat_project_id(project_name)
  xnat_epad_project_url = xnat_base_url + xnat_project_base + xnat_project_id
  xnat_epad_project_subject_url = xnat_epad_project_url+'/subjects/'
  xnat_subject_id = patient_name_to_xnat_subject_id(patient_name)
  #payload = { 'ID': xnat_subject_id, 'label': xnat_subject_id } # Subject labels are sensitive in XNAT  
  payload = {}
  xnat_subject_url = xnat_epad_project_subject_url + xnat_subject_id
  cookies = dict(JSESSIONID=jsessionid)
  r = requests.put(xnat_subject_url, params=payload, cookies=cookies)
  if r.status_code == requests.codes.ok:
    print 'Subject', patient_name, 'already exists in XNAT'
  elif r.status_code == requests.codes.created:
    print 'Subject', patient_name, 'created'
  else:
    print 'Warning: failed to create subject', patient_name, 'with id', xnat_subject_id, '- status code =', r.status_code

def create_subjects(xnat_base_url, jsessionid, project_name, subject_study_pairs):  
  for patient_name, _ in subject_study_pairs:
    create_subject(xnat_base_url, jsessionid, project_name, patient_name)

def create_experiment(xnat_base_url, jsessionid, project_name, patient_name, study_uid):
  xnat_project_id = project_name_to_xnat_project_id(project_name)
  xnat_epad_project_url = xnat_base_url + xnat_project_base + xnat_project_id
  xnat_epad_project_subject_url = xnat_epad_project_url+'/subjects/'
  xnat_subject_id = patient_name_to_xnat_subject_id(patient_name)
  xnat_experiment_id = study_uid_to_xnat_experiment_id(study_uid)
  payload = { 'label': study_uid, 'xsiType': 'xnat:otherDicomSessionData' }
  xnat_experiment_url = xnat_epad_project_subject_url + xnat_subject_id + '/experiments/' + xnat_experiment_id
  cookies = dict(JSESSIONID=jsessionid)
  r = requests.put(xnat_experiment_url, params=payload, cookies=cookies)
  if r.status_code == requests.codes.ok:
    print 'Experiment for study', study_uid, 'already existed in XNAT'
  elif r.status_code == requests.codes.created:
    print 'Experiment for study', study_uid, 'created'
  else:
    print 'Warning: failed to create experiment for tudy', study_uid, '- status code =', r.status_code

def create_experiments(xnat_base_url, jsessionid, project_name, subject_study_pairs):
  for patient_name, study_uid in subject_study_pairs:
    create_experiment(xnat_base_url, jsessionid, project_name, patient_name, study_uid)
