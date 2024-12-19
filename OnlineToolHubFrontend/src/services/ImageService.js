/**
 * 
 */
import axios from 'axios'

const REST_API_BASE_URL = "http://localhost:8080/api/images"

export const grayScale = (files) => axios.post(REST_API_BASE_URL + "/process", files, {
    responseType: 'blob',
  })
  
export default {
	grayScale
}