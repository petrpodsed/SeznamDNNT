import { Component, OnInit, Output, EventEmitter  } from '@angular/core';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { MatDialog } from '@angular/material/dialog';
import { AppService } from 'src/app/app.service';
import { AppState } from 'src/app/app.state';
import { DialogLoginComponent } from '../dialog-login/dialog-login.component';
import { DialogRegistrationComponent } from '../dialog-registration/dialog-registration.component';
import { User } from 'src/app/shared/user';
import { Router } from '@angular/router';
import { AppConfiguration } from 'src/app/app-configuration';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss']
})
export class NavbarComponent implements OnInit {
  
  // sidenav
  @Output() public sidenavToggle = new EventEmitter();

  now = new Date();

  //simpleLogin: boolean = false;

  constructor(
    private dialog: MatDialog,
    private router: Router,
    private service: AppService,
    public state: AppState,
    public config: AppConfiguration
    ) { }

  ngOnInit(): void {
  }

  changeLang(lang: string) {
    this.service.changeLang(lang);
  }


  showLogin() {
    this.dialog.open(DialogLoginComponent, {
      width: '450px',
      panelClass: 'app-dialog-login',
      data: null
    });
  }

  showUser() {
    if (!this.state.user.thirdpartyuser) {
      const d = this.dialog.open(DialogRegistrationComponent, {
        width: '600px',
        panelClass: 'app-dialog-login',
        data: {isRegister: false}
      });
    }
  }

  logout() {
    this.service.logout().subscribe(res => {
      this.state.setLogged(res);
      this.state.logged = false;
      this.state.user = null;

      this.state.currentZadost={
        VN: null,
        NZN: null, 
        VNX:null
      };

      localStorage.removeItem('user');
      sessionStorage.clear();
      this.router.navigate(['/']);
    });
  }

  register() {
    
    const d = this.dialog.open(DialogRegistrationComponent, {
      width: '600px',
      panelClass: 'app-register-dialog',
      data: {isRegister: true}
    });


  }


  wayfLink() {
    // configuration is on the server its redirect to identity provider or wayflink
    window.location.href = "/api/user/shib_login_redirect";
  }

  profileEnabled(): boolean {
    if (this.state.user.thirdpartyuser) return false;
    else return true;
  }

  changePassEnabled(): boolean {
    if (this.state.user.thirdpartyuser) return false;
    else return true;
  } 

  // sidenav fuction
  public onToggleSidenav = () => {
    this.sidenavToggle.emit();
  }
  
  resetPasswd() {
    if (!this.state.user.thirdpartyuser) {
      this.router.navigate(['/userpswd'], {});
    }
  }

}
