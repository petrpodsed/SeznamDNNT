/*
 * SDNNT API
 *  API umožnuje vzdáleně spravovat svoje žádosti, vytvářet nové, prohlížet již poslané a procházet katalog.
 *
 * OpenAPI spec version: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */

package cz.inovatika.sdnnt.openapi.endpoints.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.*;
import javax.validation.Valid;

/**
 * BatchResponse
 */
@javax.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.JavaJerseyServerCodegen", date = "2021-07-15T08:56:28.035Z[GMT]")public class BatchResponse   {
  @JsonProperty("saved")
  private List<SavedRequest> saved = null;

  @JsonProperty("notsaved")
  private List<NotSavedRequest> notsaved = null;

  public BatchResponse saved(List<SavedRequest> saved) {
    this.saved = saved;
    return this;
  }

  public BatchResponse addSavedItem(SavedRequest savedItem) {
    if (this.saved == null) {
      this.saved = new ArrayList<SavedRequest>();
    }
    this.saved.add(savedItem);
    return this;
  }

  /**
   * Get saved
   * @return saved
   **/
  @JsonProperty("saved")
  @Schema(description = "")
  @Valid
  public List<SavedRequest> getSaved() {
    return saved;
  }

  public void setSaved(List<SavedRequest> saved) {
    this.saved = saved;
  }

  public BatchResponse notsaved(List<NotSavedRequest> notsaved) {
    this.notsaved = notsaved;
    return this;
  }

  public BatchResponse addNotsavedItem(NotSavedRequest notsavedItem) {
    if (this.notsaved == null) {
      this.notsaved = new ArrayList<NotSavedRequest>();
    }
    this.notsaved.add(notsavedItem);
    return this;
  }

  /**
   * Get notsaved
   * @return notsaved
   **/
  @JsonProperty("notsaved")
  @Schema(description = "")
  @Valid
  public List<NotSavedRequest> getNotsaved() {
    return notsaved;
  }

  public void setNotsaved(List<NotSavedRequest> notsaved) {
    this.notsaved = notsaved;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BatchResponse batchResponse = (BatchResponse) o;
    return Objects.equals(this.saved, batchResponse.saved) &&
        Objects.equals(this.notsaved, batchResponse.notsaved);
  }

  @Override
  public int hashCode() {
    return Objects.hash(saved, notsaved);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BatchResponse {\n");
    
    sb.append("    saved: ").append(toIndentedString(saved)).append("\n");
    sb.append("    notsaved: ").append(toIndentedString(notsaved)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
