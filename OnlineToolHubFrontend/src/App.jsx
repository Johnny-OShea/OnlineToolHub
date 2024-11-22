import Container from '@mui/material/Container';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import Header from './components/Header';
import Home from './components/Home';

export function App() {
  return (
    <Container maxWidth="sm">
      <Box sx={{ my: 4 }}>
	  	<Header />
        <Typography variant="h4" component="h1" sx={{ mb: 2 }}>
          Material UI Vite.js example
        </Typography>
		<Home />
        {/*<ProTip />
        <Copyright /> */}
      </Box>
    </Container>
  );
}

export default App;
