import { BrowserModule } from '@angular/platform-browser';
import { APP_INITIALIZER, NgModule } from '@angular/core';
import { MAT_DATE_LOCALE } from '@angular/material/core';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeComponent } from './pages/home/home.component';
import { HelpComponent } from './pages/help/help.component';
import { AccountComponent } from './pages/account/account.component';
import { AdminComponent } from './pages/admin/admin.component';
import { SearchComponent } from './pages/search/search.component';
import { FooterComponent } from './components/footer/footer.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { SearchBarComponent } from './components/searchbar/searchbar.component';
import { ResultItemComponent } from './components/result-item/result-item.component';
import { FacetsComponent } from './components/facets/facets.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { AppConfiguration } from './app-configuration';
import { AppService } from './app.service';
import { AppState } from './app.state';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { AppMaterialModule } from './app-material.module';
import { FlexLayoutModule } from '@angular/flex-layout';
import { AngularEditorModule } from '@kolkov/angular-editor';
import { PaginatorComponent } from './components/paginator/paginator.component';
import { DialogHistoryComponent } from './components/dialog-history/dialog-history.component';
import { DialogStatesComponent } from './components/dialog-states/dialog-states.component';
import { DialogLoginComponent } from './components/dialog-login/dialog-login.component';
import { DialogIdentifierComponent } from './components/dialog-identifier/dialog-identifier.component';
import { UserPswDialogComponent } from './components/user-pswdialog/user-pswdialog.component';

import { SidenavListComponent } from './components/sidenav-list/sidenav-list.component';
import { FacetsUsedComponent } from './components/facets/facets-used/facets-used.component';
import { AccountItemComponent } from './components/account-item/account-item.component';
import { UserFormComponent } from './components/user-form/user-form.component';
import { UserDialogComponent } from './components/user-dialog/user-dialog.component';
import { DialogSendRequestComponent } from './components/dialog-send-request/dialog-send-request.component';
import { ZadostComponent } from './pages/zadost/zadost.component';
import { DialogPromptComponent } from './components/dialog-prompt/dialog-prompt.component';
import { ExpressionDialogComponent } from './components/expression-dialog/expression-dialog.component';
import { ImportsComponent } from './pages/imports/imports.component';
import { ImportComponent } from './pages/import/import.component';
import { ResetPasswordComponent } from './pages/reset-password/reset-password.component';

import { MatPasswordStrengthModule } from '@angular-material-extensions/password-strength';
import { GranularityComponent } from './components/granularity/granularity.component';
import { InputLoginNameComponent } from './pages/input-login-name/input-login-name.component';
import { InputLoginNameDialogComponent } from './components/input-login-name-dialog/input-login-name-dialog.component';
import { ConsentComponent } from './components/consent/consent.component';
import { CookieModule } from 'ngx-cookie';


export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http);
}

export function createTranslateLoader(http: HttpClient) {
  return new TranslateHttpLoader(http, 'assets/i18n/', '.json');
}

const providers: any[] =[
  { provide: MAT_DATE_LOCALE, useValue: 'cs-CZ' },
  AppState, AppConfiguration, HttpClient, 
  { provide: APP_INITIALIZER, useFactory: (config: AppConfiguration) => () => config.load(), deps: [AppConfiguration], multi: true },
  DatePipe, DecimalPipe, AppService
];

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    HelpComponent,
    AccountComponent,
    AdminComponent,
    SearchComponent,
    FooterComponent,
    NavbarComponent,
    SearchBarComponent,
    ResultItemComponent,
    FacetsComponent,
    PaginatorComponent,
    DialogHistoryComponent,
    DialogStatesComponent,
    DialogLoginComponent,
    DialogIdentifierComponent,
    UserPswDialogComponent,
    InputLoginNameDialogComponent,

    SidenavListComponent,
    FacetsUsedComponent,
    AccountItemComponent,
    UserFormComponent,
    UserDialogComponent,
    DialogSendRequestComponent,
    ZadostComponent,
    DialogPromptComponent,
    ExpressionDialogComponent,
    ImportsComponent,
    ImportComponent,
    ResetPasswordComponent,

    GranularityComponent,
    InputLoginNameComponent,
    ConsentComponent,
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    CommonModule,
    AppRoutingModule,
    AppMaterialModule,
    FlexLayoutModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: (createTranslateLoader),
        deps: [HttpClient]
      }
    }),
    AppRoutingModule,
    MatPasswordStrengthModule.forRoot(),
    BrowserAnimationsModule,
    AngularEditorModule,
    CookieModule.forRoot()
  ],
  providers,
  bootstrap: [AppComponent]
})
export class AppModule { }
