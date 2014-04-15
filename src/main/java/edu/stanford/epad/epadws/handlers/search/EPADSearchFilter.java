package edu.stanford.epad.epadws.handlers.search;

import java.util.Set;

/**
 * 
 * 
 * 
 * @author martin
 * @see EPADSearchFilterBuilder
 */
public class EPADSearchFilter
{
	private String projectNameMatch = null;
	private String patientNameMatch = null;
	private String patientIDMatch = null;
	private String accessionNumberMatch = null;
	private String modalityMatch = null;
	private AnnotationMatch annotationMatch = AnnotationMatch.NONE;
	private String insertDateStartMatch = null;
	private String insertDateFinishMatch = null;
	private String studyDateStartMatch = null;
	private String studyDateFinishMatch = null;

	public String getProjectNameMatch()
	{
		return this.projectNameMatch;
	}

	public boolean hasProjectNameMatch()
	{
		return this.projectNameMatch != null;
	}

	public void setProjectNameMatch(String projectNameMatch)
	{
		this.projectNameMatch = projectNameMatch;
	}

	public boolean projectNameMatches(String projectName)
	{
		if (this.projectNameMatch == null)
			return true;
		else {
			String projectNameRegex = ".*" + projectNameMatch + ".*";
			if (projectName.matches(projectNameRegex))
				return true;
			else
				return false;
		}
	}

	public String getPatientNameMatch()
	{
		return this.patientNameMatch;
	}

	public boolean hasPatientNameMatch()
	{
		return this.patientNameMatch != null;
	}

	public void setPatientNameMatch(String patientNameMatch)
	{
		this.patientNameMatch = patientNameMatch;
	}

	public boolean patientNameMatches(String patientName)
	{
		if (this.patientNameMatch == null)
			return true;
		else {
			String patientNameRegex = ".*" + patientNameMatch + ".*";
			if (patientName.matches(patientNameRegex))
				return true;
			else
				return false;
		}
	}

	public String getPatientIDMatch()
	{
		return this.patientIDMatch;
	}

	public boolean hasPatientIDMatch()
	{
		return this.patientIDMatch != null;
	}

	public void setPatientIDMatch(String patientIDMatch)
	{
		this.patientIDMatch = patientIDMatch;
	}

	public boolean patientIDMatches(String patientID)
	{
		if (this.patientIDMatch == null)
			return true;
		else {
			String patientIDRegex = ".*" + patientIDMatch + ".*";
			if (patientID.matches(patientIDRegex))
				return true;
			else
				return false;
		}
	}

	public String getAccessionNumberMatch()
	{
		return this.accessionNumberMatch;
	}

	public boolean hasAccessionNumberMatch()
	{
		return this.accessionNumberMatch != null;
	}

	public void setAccessionNumberMatch(String accessionNumberMatch)
	{
		this.accessionNumberMatch = accessionNumberMatch;
	}

	public boolean accessionNumberMatches(String accessionNumber)
	{
		if (this.accessionNumberMatch == null)
			return true;
		else {
			String accessionNumberRegex = ".*" + accessionNumberMatch + ".*";
			if (accessionNumber.matches(accessionNumberRegex))
				return true;
			else
				return false;
		}
	}

	public String getModalityMatch()
	{
		return this.modalityMatch;
	}

	public boolean hasModalityMatch()
	{
		return this.modalityMatch != null;
	}

	public void setModalityMatch(String modalityMatch)
	{
		this.modalityMatch = modalityMatch;
	}

	public boolean modalitiesMatch(Set<String> modalities)
	{
		for (String modality : modalities)
			if (modalityMatches(modality))
				return true;
		return false;
	}

	public boolean modalityMatches(String modality)
	{
		if (this.modalityMatch == null)
			return true;
		else {
			String modalityRegex = ".*" + modalityMatch + ".*";
			if (modality.matches(modalityRegex))
				return true;
			else
				return false;
		}
	}

	public boolean hasAnnotationMatch()
	{
		return this.annotationMatch != AnnotationMatch.NONE;
	}

	public boolean hasAnnotationsAnnotationMatch()
	{
		return this.annotationMatch == AnnotationMatch.HAS_ANNOTATIONS;
	}

	public void setHasAnnotationsAnnotationMatch()
	{
		this.annotationMatch = AnnotationMatch.HAS_ANNOTATIONS;
	}

	public boolean hasNoAnnotationsAnnotationMatch()
	{
		return this.annotationMatch == AnnotationMatch.HAS_NO_ANNOTATIONS;
	}

	public void setHasNoAnnotationsAnnotationMatch()
	{
		this.annotationMatch = AnnotationMatch.HAS_NO_ANNOTATIONS;
	}

	public boolean annotationNumberMatches(int numberOfAnnotations)
	{
		if (this.annotationMatch == null)
			return true;
		else {
			if (this.annotationMatch == AnnotationMatch.NONE)
				return true;
			else if (this.annotationMatch == AnnotationMatch.HAS_ANNOTATIONS)
				return numberOfAnnotations > 0;
			else
				// AnnotationMatch.HAS_NO_ANNOTATIONS
				return numberOfAnnotations == 0;
		}
	}

	public String getInsertDateStartMatch()
	{
		return this.insertDateStartMatch;
	}

	public boolean hasInsertDateStartMatch()
	{
		return this.insertDateStartMatch != null;
	}

	public void setInsertDateStartMatch(String insertDateStartMatch)
	{
		this.insertDateStartMatch = insertDateStartMatch;
	}

	public String getInsertDateFinishMatch()
	{
		return this.insertDateFinishMatch;
	}

	public boolean hasInsertDateFinishMatch()
	{
		return this.insertDateFinishMatch != null;
	}

	public void setInsertDateFinishMatch(String insertDateFinishMatch)
	{
		this.insertDateFinishMatch = insertDateFinishMatch;
	}

	public String getStudyDateStartMatch()
	{
		return this.studyDateStartMatch;
	}

	public boolean hasStudyDateStartMatch()
	{
		return this.studyDateStartMatch != null;
	}

	public void setStudyDateStartMatch(String studyDateStartMatch)
	{
		this.studyDateStartMatch = studyDateStartMatch;
	}

	public String getStudyDateFinishMatch()
	{
		return this.studyDateFinishMatch;
	}

	public boolean hasStudyDateFinishMatch()
	{
		return this.studyDateFinishMatch != null;
	}

	public void setStudyDateFinishMatch(String studyDateFinishMatch)
	{
		this.studyDateFinishMatch = studyDateFinishMatch;
	}

	public boolean shouldFilterProject(String projectName, int numberOfAnnotations)
	{
		return (this.hasProjectNameMatch() && !projectNameMatches(projectName))
				|| (this.hasAnnotationMatch() && !annotationNumberMatches(numberOfAnnotations));
	}

	public boolean shouldFilterSubject(String patientID, String patientName, Set<String> examTypes,
			int numberOfAnnotations)
	{
		return (this.hasPatientIDMatch() && !patientIDMatches(patientID))
				|| (this.hasPatientNameMatch() && !patientNameMatches(patientName))
				|| (this.hasModalityMatch() && !modalitiesMatch(examTypes))
				|| (this.hasAnnotationMatch() && !annotationNumberMatches(numberOfAnnotations));
	}

	public boolean shouldFilterStudy(String patientID, String studyAccessionNumber, Set<String> examTypes,
			int numberOfAnnotations)
	{
		return (this.hasPatientIDMatch() && !patientIDMatches(patientID))
				|| (this.hasAccessionNumberMatch() && !accessionNumberMatches(studyAccessionNumber))
				|| (this.hasModalityMatch() && !modalitiesMatch(examTypes))
				|| (this.hasAnnotationMatch() && !annotationNumberMatches(numberOfAnnotations));
	}

	public boolean shouldFilterSeries(String patientID, String patientName, String examType, int numberOfAnnotations)
	{
		return (this.hasPatientIDMatch() && !patientIDMatches(patientID))
				|| (this.hasPatientNameMatch() && !patientNameMatches(patientName))
				|| (this.hasModalityMatch() && !modalityMatches(examType))
				|| (this.hasAnnotationMatch() && !annotationNumberMatches(numberOfAnnotations));
	}

	private enum AnnotationMatch {
		NONE, HAS_ANNOTATIONS, HAS_NO_ANNOTATIONS
	};
}
