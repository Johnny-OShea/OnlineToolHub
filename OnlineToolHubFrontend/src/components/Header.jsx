import {AppBar, Toolbar, Typography, Button, IconButton, MenuItem } from '@mui/material';

const Header = () => {

  return (
    <AppBar position="static">
      <Toolbar>
        <img
          src="assets/images/online-tool-hub-logo.png"
          alt="Online Tool Hub Logo"
        />
        <Typography variant="h6">
          Online Tool Hub
        </Typography>
        <Button color="inherit" href="#features">
          Services
        </Button>
        <Button color="inherit" href="#pricing">
          Pricing
        </Button>
        <Button color="inherit" href="#about">
          About
        </Button>
        <Button color="inherit" href="#contact">
          Contact
        </Button>
        <Button color="inherit" href="/sign-in">
          Sign In
        </Button>
        <Button variant="outlined" color="inherit">
          Free Trial
        </Button>
        <IconButton color="inherit" edge="end">
          <MenuItem />
        </IconButton>
      </Toolbar>
    </AppBar>
  );
};

export default Header;