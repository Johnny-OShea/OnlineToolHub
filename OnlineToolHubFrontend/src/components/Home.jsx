import { useRef } from 'react'
import { grayScale } from '../services/ImageService'

const Home = () => {
	// Create a ref to access the file input element directly
	const fileInputRef = useRef()

	const handleUpload = () => {
		fileInputRef.current.click();
	};

	const handleChange = (event) => {
	  const files = event.target.files;

	  // Log the files to see if they are being captured
	  console.log('Selected files:', files);

	  const formData = new FormData();

	  // Append each file to the form data
	  for (let i = 0; i < files.length; i++) {
	    formData.append('images', files[i]);
	  }

	  // Log each entry in the FormData
	  for (let [key, value] of formData.entries()) {
	    console.log(`FormData entry: ${key}`, value);
	  }

	  // Ask the backend to grayscale all the images
	  grayScale(formData)
	    .then((response) => {
	      // Create a URL for the downloaded ZIP file
	      const url = window.URL.createObjectURL(new Blob([response.data]));
	      const link = document.createElement('a');
	      link.href = url;
	      link.setAttribute('download', 'processed_images.zip');
	      document.body.appendChild(link);
	      link.click();
	      link.remove();
	    })
	    .catch((error) => {
	      console.error('Error processing images:', error);
	    });
	};


	return (
	    <div className="home-container">
	      <button
	        className="button upload-button w-button"
	        onClick={handleUpload}
	      >
	        Upload Images
	      </button>

	      {/* Hidden file input */}
	      <input
	        type="file"
	        multiple
	        ref={fileInputRef}
	        onChange={handleChange}
	        hidden
	      />
	    </div>
	  );
	};

export default Home;
