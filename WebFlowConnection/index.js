// Ensure Webflow scripts are loaded
//window.Webflow ||= [];
//window.Webflow.push(() => {
	console.log("Webflow is ready to use.");
	// Select the button and file input
	const fileButton = document.querySelector("#generate-text-button");
	const fileInput = document.querySelector("#file-upload");

	// Function to handle file input click
	fileButton.addEventListener("click", () => {
		fileInput.click();
	});

	// Function to handle file input change
	fileInput.addEventListener("change", (event) => {
		const files = event.target.files;
		console.log("Selected files:", files);

		const formData = new FormData();

		// Append files to FormData
		for (let i = 0; i < files.length; i++) {
			formData.append("images", files[i]);
		}

		// Log FormData for debugging
		for (let [key, value] of formData.entries()) {
			console.log(`FormData entry: ${key}`, value);
		}

		// Send the files to the backend for processing
		generateAltText(formData)
			.then((response) => {
				// Create a URL for the response data
				const url = window.URL.createObjectURL(new Blob([response]));
				const link = document.createElement("a");
				link.href = url;
				link.setAttribute("download", "processed_images.zip");
				document.body.appendChild(link);
				link.click();
				link.remove();
			})
			.catch((error) => {
				console.error("Error processing images:", error);
			});
	});

	const REST_API_BASE_URL = "http://localhost:8080/api/images";

	const generateAltText = (files) => {
		return fetch(`${REST_API_BASE_URL}/process`, {
			method: "POST",
			body: files,
		})
			.then((response) => {
				if (!response.ok) {
					throw new Error("Network response was not ok");
				}
				return response.blob();
			});
	};
//});

