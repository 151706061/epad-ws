package edu.stanford.epad.epadws.dcm4chee;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.epad.common.dicom.DCM4CHEEImageDescription;
import edu.stanford.epad.common.dicom.DICOMFileDescription;
import edu.stanford.epad.dtos.internal.DCM4CHEEStudySearchType;
import edu.stanford.epad.epadws.handlers.core.ImageReference;
import edu.stanford.epad.epadws.queries.Dcm4CheeQueries;

/**
 * Defines all operations on the dcm4chee database used by ePAD.
 * 
 * @author martin
 * @see Dcm4CheeQueries
 */
public interface Dcm4CheeDatabaseOperations
{
	String getStudyUIDForSeries(String seriesUID);

	String getSeriesUIDForImage(String imageUID);

	Map<String, String> studySearch(String studyUID);

	List<Map<String, String>> getAllSeriesInStudy(String studyUID);

	Set<String> getAllSeriesUIDsInStudy(String studyUID);

	Set<String> getStudyUIDsForPatient(String patientID);

	Set<String> getImageUIDsForSeries(String seriesUID);

	String getFirstImageUIDInSeries(String seriesUID);

	int getNumberOfStudiesForPatient(String patientID);

	int getNumberOfStudiesForPatients(Set<String> patientIDs);

	Map<String, String> getParentStudyForSeries(String seriesUID);

	public Set<DICOMFileDescription> getDICOMFilesForSeries(String seriesUID);

	List<DCM4CHEEImageDescription> getImageDescriptions(String studyUID, String seriesUID);

	DCM4CHEEImageDescription getImageDescription(ImageReference imageReference);

	DCM4CHEEImageDescription getImageDescription(String studyUID, String seriesUID, String imageUID);

	int getPrimaryKeyForImageUID(String imageUID);

	/**
	 * Get all dcm4chee studies that have finished processing.
	 */
	Set<String> getAllReadyDcm4CheeSeriesUIDs();

	/**
	 * typeValue one of: patientName, patientId, studyDate, accessionNum, examType
	 * 
	 * @see DCM4CHEEStudySearchType
	 */
	List<Map<String, String>> studySearch(DCM4CHEEStudySearchType searchType, String typeValue);

	/**
	 * Returns a map describing a dcm4chee series with the following keys: pk, study_fk, mpps_fk, inst_code_fk,
	 * series_iuid, series_no, modality, body_part, laterality, series_desc, institution, station_name, department,
	 * perf_physician, perf_phys_fn_sx, perf_phys_gn_sx perf_phys_i_name, perf_phys_p_name, pps_start, series_custom1,
	 * series_custom2, series_custom3, num_instances, src_aet, ext_retr_aet, retrieve_aets, fileset_iuid, fileset_id,
	 * availability, series_status, created_time, updated_time, series_attrs
	 */
	Map<String, String> getSeriesData(String seriesUID);
}
